package com.vivi.scarm.test

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.scalatest._

import com.vivi.scarm._

import TestObjects._

case class TeacherId(id: Int) extends AnyVal
case class Teacher(id: TeacherId, name: String) extends Entity[TeacherId]

case class IntId(id: Int)
case class StringId(string: Int)

case class IntEntity(id: Int, name: String, intval: Option[String])
    extends Entity[Int]

case class StringEntity(id: StringId, name: String, strval: Option[String], intval: Option[Int])
    extends Entity[StringId]

object DSLSuite {
  val hsqldbCleanup = (xa:Transactor[IO]) => {
    val op = for { 
      _ <- sql"""DROP SCHEMA PUBLIC CASCADE""".update.run
      _ <- sql"""SHUTDOWN IMMEDIATELY""".update.run
    } yield false
    op.transact(xa).unsafeRunSync()
  }
}

class DSLSuite extends Suites(
  new DSLTest("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:file:testdb",
    "SA", "", DSLSuite.hsqldbCleanup),
  new DSLTest("org.postgresql.Driver", "jdbc:postgresql:scarm", "scarm", "scarm"),
  new DSLTest("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/scarm?serverTimezone=UTC&useSSL=false", "scarm", "scarm")
)

class DSLTest(driver: String,
  url: String,
  username: String,
  pass: String,
  cleanup: (Transactor[IO] => Boolean) = (_ => true ) 
) extends FunSuite with BeforeAndAfterAll {

  implicit val xa = Transactor.fromDriverManager[IO](driver, url, username, pass)

  val teachers = Table[TeacherId,Teacher]("teachers")

  val tables = Seq(teachers)

  private def run[T](op: ConnectionIO[T]): T = op.transact(xa).unsafeRunSync()

  private def createAll() = 
    for (t <- tables) {
      t.create().transact(xa).unsafeRunSync()
    }

  private def dropAll(xa: Transactor[IO]): Unit = 
    for (t <-tables) {
      try {
        t.dropCascade.transact(xa).unsafeRunSync()
      } catch { case e: Exception =>
          info(s"failed dropping ${t.name} ${e.getMessage()}")
      }
    }

  override def beforeAll() {
    createAll()
  }

  override def afterAll() {
    cleanup(xa)
    if (cleanup(xa))  dropAll(xa) else ()
  }


  test("after inserting an entity, it can be selected by primary key") {
    val t1 = Teacher(TeacherId(1),  "entity1")
    val t2 = Teacher(TeacherId(2),  "entity2")
    val op = for {
      n1 <- teachers.insert(t1)
      n2 <- teachers.insert(t2)
      t2Result <- teachers.query(t2.id)
      t1Result <- teachers.query(t1.id)
    } yield {
      assert(n1 == 1)
      assert(n2 == 1)
      assert(t1Result == Some(t1))
      assert(t2Result == Some(t2))
    }
    run(op)
  }


  test("after deleting an entity, the entity cannot be found by primary key") {
    val t = Teacher(TeacherId(3), "A Teacher")
    val op = for {
      _ <- teachers.insert(t)
      _ <- teachers.delete(t.id)
      result <- teachers.query(t.id)
    } yield {
      assert(result == None)
    }
    run(op)
  }

  test("after updating an entity, selecting the entity by primary key returns the new entity") (pending)

  test("Update doesn't compile if primary key isn't a prefix") (pending)

  test("after dropping a table, the table cannot be used for inserts or selects") (pending)

  test("SQL on a table with date fields") (pending)

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

  test("Query by Unique Index") (pending)

  test("Query by Unique Index with no results") (pending)

  test("Query by Foreign Key") (pending)

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
}
