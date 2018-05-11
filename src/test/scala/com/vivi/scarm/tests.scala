package com.vivi.scarm.test

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import java.sql.{ SQLException }
import java.time._

import org.scalatest._

import com.vivi.scarm._
import com.vivi.scarm.FieldMap._

import TestObjects._
import MySQLHacks.JavaTimeLocalDateMeta

object DSLSuite {
  val hsqldbCleanup = (xa:Transactor[IO]) => {
    val op = for {
      _ <- sql"DROP SCHEMA PUBLIC CASCADE".update.run
      s <- sql"SHUTDOWN IMMEDIATELY".update.run
    } yield false
    op.transact(xa).unsafeRunSync()
  }
}

case class IntRow(id: Int, name: String) extends Entity[Int]

case class DateRow(id: Int,
  instant: Instant = Instant.now,
  localDate: LocalDate = LocalDate.now,
  localDateTime: LocalDateTime = LocalDateTime.now,
  localTimex: LocalTime = LocalTime.now
) extends Entity[Int]

class DSLSuite extends Suites(
  new DSLTest("org.hsqldb.jdbc.JDBCDriver",
    "jdbc:hsqldb:file:testdb",
    "SA", "",
    MySQLHacks.deactivate, 
    DSLSuite.hsqldbCleanup),

  new DSLTest("org.postgresql.Driver",
    "jdbc:postgresql:scarm", "scarm", "scarm",
    MySQLHacks.deactivate
  ),

  new DSLTest("com.mysql.cj.jdbc.Driver",
    "jdbc:mysql://localhost:3306/scarm?serverTimezone=UTC&useSSL=false&sql_mode=",
    "scarm", "scarm",
    MySQLHacks.activate
  )
)


class DSLTest(driver: String,
  url: String,
  username: String,
  pass: String,
  initialize: => Unit,
  cleanup: (Transactor[IO] => Boolean) = (_ => true ) 
) extends FunSuite with BeforeAndAfterAll {

  info(s"Testing with url ${url}")

  var idCounter = new java.util.concurrent.atomic.AtomicInteger (1)

  def nextId: Int = idCounter.getAndAdd(1)

  implicit val xa = Transactor.fromDriverManager[IO](driver, url, username, pass)

  private def run[T](op: ConnectionIO[T]): T = op.transact(xa).unsafeRunSync()

  private def runQuietly(op: ConnectionIO[_]) = try {
    run(op)
  } catch { case _:Exception => }

  private def createAll() = 
    for (t <- allTables) {
      t.create().transact(xa).unsafeRunSync()
    }

  private def dropAll(xa: Transactor[IO]): Unit = 
    for (t <-allTables) {
      try {
        t.dropCascade.transact(xa).unsafeRunSync()
      } catch { case e: Exception =>
          println(s"failed dropping ${t.name} ${e.getMessage()}")
      }
    }

  override def beforeAll() {
    initialize
    createAll()
  }

  override def afterAll() {
    cleanup(xa)
    if (cleanup(xa))  dropAll(xa) else ()
  }

  test("after inserting an entity, it can be selected by primary key") {
    val t1 = Teacher(TeacherId(nextId),  "entity1")
    val t2 = Teacher(TeacherId(nextId),  "entity2")
    val op = for {
      n <- teachers.insert(t1, t2)
      t2Result <- teachers(t2.id)
      t1Result <- teachers(t1.id)
    } yield {
      assert(n == 2)
      assert(t1Result == Some(t1))
      assert(t2Result == Some(t2))

    }
    run(op)
  }


  test("Multiple entities can be inserted in one operation") {
    val newTeachers = Set(Teacher(TeacherId(nextId),"Fred"),
      Teacher(TeacherId(nextId),"Barney"),
      Teacher(TeacherId(nextId), "Wilma")
    )
    assert(newTeachers.size == run(teachers.insert(newTeachers.toSeq: _*)))
  }


  test("A table scan returns all the entities in the table") {
    val table = Table[Int,IntRow]("scan_test", Seq("id"))
    val entities = Set(IntRow(1,"One"), IntRow(2,"Two"), IntRow(3, "three"))
    val op = for {
      _ <- table.create()
      _ <- table.insert(entities.toSeq: _*)
      results <- table.scan(Unit)
      _ <-table.drop
    } yield {
      assert(results == entities)
    }
    run(op)
  }

  test("after deleting an entity, the entity cannot be found by primary key") {
    val t = Teacher(TeacherId(nextId), "A Teacher")
    val op = for {
      _ <- teachers.insert(t)
      _ <- teachers.delete(t.id)
      result <- teachers(t.id)
    } yield {
      assert(result == None)
    }
    run(op)
  }

  test("only the specified entity is affected by a delete") {
    val t1 = Teacher(TeacherId(nextId), "Teacher 14")
    val t2 = Teacher(TeacherId(nextId), "Teacher 15")
    val op = for {
      _ <- teachers.insert(t1,t2)
      _ <- teachers.delete(t1.id)
      r1 <- teachers(t1.id)
      r2 <- teachers(t2.id)
    } yield {
      assert (r1 == None)
      assert (r2 == Some(t2))
    }
  }

  test("deleting a nonexistent entity affects nothing") {
    val n = teachers.delete(TeacherId(-1))
    assert (run(n) == 0)
  }

  test("multiple entities can be deleted in one operation")  {
    val t1 = Teacher(TeacherId(nextId),  "entity1")
    val t2 = Teacher(TeacherId(nextId),  "entity2")
    val op = for {
      _ <- teachers.insert(t1,t2)
      n <- teachers.delete(t1.id,t2.id, TeacherId(-1))
      t1Result <- teachers(t1.id)
      t2Result <- teachers(t2.id)
    } yield {
      assert(n == 2)
      assert(t1Result == None)
      assert(t2Result == None)
    }
    run(op)
  }

  test("after updating an entity, selecting the entity by primary key returns the new entity") {
    val t = Teacher(TeacherId(nextId), "A Teacher")
    val newT = t.copy(name="Updated")
    val op = for {
      _ <- teachers.insert(t)
      n <- teachers.update(newT)
      result <- teachers(t.id)
    } yield {
      assert(n == 1)
      assert(result == Some(newT))
    }
    run(op)
  }

  test("only the specified entity is affected by an update") {
    val t1 = Teacher(TeacherId(nextId), "A Teacher")
    val t2 = Teacher(TeacherId(nextId), "A Teacher")
    val newT = t1.copy(name="Updated")
    val op = for {
      _ <- teachers.insert(t1,t2)
      n <- teachers.update(newT)
      result <- teachers(t2.id)
    } yield {
      assert(n == 1)
      assert(result == Some(t2))
    }
    run(op)
  }

  test("updating a nonexistent entity affects nothing") {
    val t = Teacher(TeacherId(-1), "A Teacher")
    assert(0 == run(teachers.update(t)))
  }

  test("Multiple entities can be updated in one operation") {
    val t1 = Teacher(TeacherId(nextId), "A Teacher")
    val t2 = Teacher(TeacherId(nextId), "A Teacher")
    val newT1 = t1.copy(name="Updated")
    val newT2 = t2.copy(name="Updated")
    val op = for {
      _ <- teachers.insert(t1,t2)
      n <- teachers.update(newT1, newT2)
    } yield {
      assert(n == 2)
    }
    run(op)
    assert(Some(newT1) == run(teachers(t1.id)))
    assert(Some(newT2) == run(teachers(t2.id)))
  }


  test("Update doesn't compile if primary key isn't a prefix") {
    val table = Table[Int,IntRow]("update_test", Seq("id"))
    val row = IntRow(1,"One")
    assertTypeError("table.update(row)")
  }

  test("a dropped table cannot be used") { 
    val table = Table[Int,IntRow]("drop_test", Seq("id"))
    run(table.create())
    run(table.drop)
    assertThrows[SQLException] {
      run(table.insert(IntRow(1,"foo")))
    }
  }

  test("Insert/Select on table with date fields") {
    val courseId = CourseId(nextId)
    val course = Course(courseId, "food", None)
    val teacherId = TeacherId(nextId)
    val teacher = Teacher(teacherId, "Teacher")
    val now = LocalDate.now(ZoneId.of("UTC"))
    val section1 = Section(SectionId(courseId, 1, 1), teacherId, "Room 1", 
      LocalTime.NOON, now.minusMonths(2), now.minusMonths(1))
    val section2 = Section(SectionId(courseId, 1, 2), teacherId, "Room 2",
      LocalTime.NOON, now.plusMonths(1), now.plusMonths(2))
    val c = Meta[java.time.LocalDate]
    val op = for {
      _ <- courses.insert(course)
      _ <- teachers.insert(teacher)
      _ <- sections.insert(section1, section2)
    } yield ()
    run(op)
    assert(run(sections(section1.id)) == Some(section1))
    assert(run(sections(section2.id)) == Some(section2))
  }

  test("Insert/Select on table with all possible date fields") {
    val table = Table[Int,DateRow]("dates", Seq("id"))
    val newDate = DateRow(nextId)
    val c = Meta[LocalDateTime]
    try {
      run(table.create())
      run(table.insert(newDate))
      val readDate = run(table(newDate.id))
      assert(!readDate.isEmpty)
      val read = readDate.get

      val originalEpoch = newDate.instant.getEpochSecond()
      val readEpoch = read.instant.getEpochSecond()
      assert(Math.abs(readEpoch - originalEpoch) <= 1)
      assert(read.localDate == newDate.localDate)

      val rdate = read.localDateTime
      val ndate = newDate.localDateTime
      assert(rdate.getYear() == ndate.getYear())
      assert(rdate.getMonth() == ndate.getMonth())
      assert(rdate.getDayOfMonth() == ndate.getDayOfMonth())
      assert(rdate.getDayOfWeek() == ndate.getDayOfWeek())
      assert(rdate.getDayOfYear() == ndate.getDayOfYear())
      assert(rdate.getHour() == ndate.getHour())
      assert(rdate.getMinute() == ndate.getMinute())
      assert(Math.abs(rdate.getSecond() - ndate.getSecond()) <= 1)

      val rtime = read.localTimex
      val ntime = newDate.localTimex
      assert(rtime.getHour() == ntime.getHour())
      assert(rtime.getMinute() == ntime.getMinute())
      assert(rtime.getSecond() == ntime.getSecond())
    }  finally {
      runQuietly(table.drop)
    }
  }

  test("SQL on a table with a primitive primary key") (pending)

  test("SQL on a table with a String primary key") (pending)

  test("SQL on a table with a compound primary key") (pending)

  test("SQL on a table with a dates in primary key") (pending)

  test("SQL on a table with nested objects") (pending)

  test("SQL on a table with a primary key containing nested object") (pending)

  test("SQL on a table with nullable String fields") (pending)

  test("SQL on a table with nullable AnyVal fields") (pending)

  test("SQL on a table with nullable nested object field") (pending)

  test("SQL on a table with autogenerated primary key") (pending)

  test("SQL on a table with explicit field names") (pending)

  test("SQL on a table with explicit key names") (pending)

  test("SQL on a table with field overrides") (pending)

  test("SQL on a table with sql type overrides") (pending)

  test("Query by Index") (pending)

  test("Query by Index with no results") (pending)

 test("Query by index returns only entities with correct key") (pending)

 test("Query by Unique Index") (pending)

 test("Query by Unique index returns only entities with correct key") (pending)

  test("Query by Unique Index with no results") (pending)

  test("Query by Foreign Key") (pending)

 test("Query by Foreign Key returns only entities with correct key") (pending)

  test("Query a View") (pending)

  test("A mandatory foreign key is a constraint")(pending)

  test("An optional foreign key is a constraint")(pending)

  test("An optional foreign key is optional")(pending)

  test("Query a Many to One Join on Mandatory Foreign Key") (pending)

  test("Many to One Join on Mandatory Foreign Key is Inner") (pending)

  test("Query a Many to One Join on Optional Foreign Key") (pending)

  test("Many to One Join on Optional Foreign Key is Outer") (pending)

  test("Query a One to Many Join") (pending)

  test("One to Many Join is Outer") (pending)

  test("Query three queries joined by many to one") (pending)

  test("Query three queries joined by one to many") (pending)

  test("Query three queries joined by one to many and many to one") (pending)

  test("Query three queries joined by many to one and one to many") (pending)

  test("Query with a Nested Join") (pending)

  test("Query with Join with compound primary key") (pending)

  test("Query with Join with primary key containing date") (pending)

  test("Query with Join with primitive primary key") (pending)

  test("Query with Join with compound primary key containing nested object") (pending)

  test("field name overrides work") (pending)

  test("sql type overrides work") (pending)

}
