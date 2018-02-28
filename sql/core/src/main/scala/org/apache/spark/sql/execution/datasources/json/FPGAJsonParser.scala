/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.json

import java.io.{Closeable, InputStream}

import org.apache.commons.io.IOUtils
import org.apache.hadoop.io.Text

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.UnsafeRow
import org.apache.spark.sql.catalyst.json.JSONOptions
import org.apache.spark.sql.catalyst.util.BadRecordException
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.Platform
import org.apache.spark.unsafe.types.UTF8String
import org.apache.spark.util.Utils
import sun.misc.Unsafe

class FPGAJsonParser(
    schema: StructType,
    val options: JSONOptions) extends Logging {

  private def initJniParser: JniParser = {
    val jp = new JniParser
    jp.setSchema(jniFields, jniTypeArray)
    jp
  }

  private def initJniParser2: JniParser2 = {
    val jp2 = new JniParser2
    jp2.setSchema(jniFields, jniTypeArray)
    jp2
  }

  val convertedRow = new UnsafeRow(schema.length)

  private def jniConverter(buf: Array[Byte], offset: Int, length: Int): UnsafeRow = {
    convertedRow.pointTo(buf, Unsafe.ARRAY_BYTE_BASE_OFFSET + offset, length)
    convertedRow
  }

  private val jniFields = schema.map(field => field.name).mkString(",")
  private val jniTypeArray =
    schema.map(field => FPGAJsonParser.typeConverter(field.dataType)).toArray

  def parseText(
      record: Text,
      recordLiteral: Text => UTF8String): Seq[InternalRow] = {
    Utils.tryWithResource(initJniParser) { parser =>
      parser.parseJson(FPGAJsonParser.textToString(record)) match {
        case null => Nil
        case byteArray => {
          val rowSize = Platform.getInt(byteArray, Unsafe.ARRAY_BYTE_BASE_OFFSET)
          Seq(jniConverter(byteArray, 4, rowSize))
        }
      }
    }
    // should throw [[[BadRecordException]]] in parser if failed
  }

  def parseInputStream(
      record: InputStream,
      recordLiteral: InputStream => UTF8String): Seq[InternalRow] = {

    Utils.tryWithResource(initJniParser) { parser =>
      parser.parseJson(FPGAJsonParser.inputStreamToString(record)) match {
        case null => Nil
        case byteArray => {
          val rowSize = Platform.getInt(byteArray, Unsafe.ARRAY_BYTE_BASE_OFFSET)
          Seq(jniConverter(byteArray, 4, rowSize))
        }
      }
    }
    // should throw BadRecordException in parser if failed
  }

  def parseFile(
      records: Iterator[Text],
      recordLiteral: Text => UTF8String): Iterator[InternalRow] = {
    Utils.tryWithResource(initJniParser2) { parser =>
      FPGAJsonParser.recordsToChunks(records).map(parser.parseJson).flatMap {
        case null => Nil.toIterator
        case byteArray => new Iterator[InternalRow] {
          private var currentPos = 0
          override def hasNext: Boolean = currentPos < byteArray.length
          override def next(): InternalRow = {
            val rowSize = Platform.getInt(byteArray, Unsafe.ARRAY_BYTE_BASE_OFFSET + currentPos)
            currentPos += 4
            val rowOffset = currentPos
            currentPos += rowSize
            jniConverter(byteArray, rowOffset, rowSize)
          }
        }
      }
    }
    // should throw [[[BadRecordException]]] in parser if failed
  }
}

private object FPGAJsonParser {
  System.loadLibrary("JniParser")

  private val boolean = 1
  private val short = 2
  private val int = 3
  private val long = 4
  private val float = 5
  private val double = 6
  private val string = 7

  private val MAX_LINES = 1024 * 128
  def typeConverter(dt: DataType): Int = dt match {
    case BooleanType => boolean
    case ShortType => short
    case IntegerType => int
    case LongType => long
    case FloatType => float
    case DoubleType => double
    case StringType => string
    case t => throw new NotImplementedError(s"Not support ${t.typeName} in jni parser")
  }
  def inputStreamToString(is: InputStream): String = {
    IOUtils.toString(is, "UTF-8")
  }
  def textToString(text: Text): String = text.toString
  def recordsToChunks(records: Iterator[Text]): Iterator[String] = {
    new Iterator[String] {
      override def hasNext: Boolean = records.hasNext
      override def next(): String = {
        records.take(MAX_LINES).map(FPGAJsonParser.textToString).mkString
      }
    }
  }
}

class JniParser extends Closeable {
  // -- Native methods
  @native def setSchema(schemaFieldNames: String, schemaFieldTypes: Array[Int]): Boolean
  @native def parseJson(s: String): Array[Byte]
  @native def close(): Unit
}

class JniParser2 extends Closeable {
  // -- Native methods
  @native def setSchema(schemaFieldNames: String, schemaFieldTypes: Array[Int]): Boolean
  @native def parseJson(s: String): Array[Byte]
  @native def close(): Unit
}
