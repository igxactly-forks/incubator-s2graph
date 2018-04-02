/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.s2graph.s2jobs.loader

import org.apache.s2graph.core.PostProcess
import org.apache.s2graph.core.storage.{CanSKeyValue, SKeyValue}
import org.apache.s2graph.s2jobs.BaseSparkTest
import org.apache.s2graph.s2jobs.serde.{LocalBulkLoaderTransformer, SparkBulkLoaderTransformer}
import play.api.libs.json.Json

class GraphFileGeneratorTest extends BaseSparkTest {

  import org.apache.hadoop.hbase.{KeyValue => HKeyValue}

  import scala.concurrent.ExecutionContext.Implicits.global

  def transformToSKeyValues(transformerMode: String, edges: Seq[String]): List[SKeyValue] = {
    transformerMode match {
      case "spark" =>
        val input = sc.parallelize(edges)
        val transformer = new SparkBulkLoaderTransformer(s2Config, options)
        val kvs = transformer.transform(input)
        kvs.flatMap { kvs =>
          kvs.map { kv =>
            CanSKeyValue.hbaseKeyValue.toSKeyValue(kv)
          }
        }.collect().toList

      case "local" =>
        val input = edges
        val transformer = new LocalBulkLoaderTransformer(s2Config, options)
        val kvs = transformer.transform(input)
        kvs.flatMap { kvs =>
          kvs.map { kv =>
            CanSKeyValue.hbaseKeyValue.toSKeyValue(kv)
          }
        }.toList
    }
  }

  test("test generateKeyValues edge only. SparkBulkLoaderTransformer") {
    val label = initTestEdgeSchema(s2, tableName, schemaVersion, compressionAlgorithm)
    /* end of initialize model */

    val bulkEdgeString = "1416236400000\tinsert\tedge\ta\tb\tfriends\t{\"since\":1316236400000,\"score\":10}"

    val transformerMode = "spark"
    val ls = transformToSKeyValues(transformerMode, Seq(bulkEdgeString))

    val serDe = s2.defaultStorage.serDe

    val bulkEdge = s2.elementBuilder.toGraphElement(bulkEdgeString, options.labelMapping).get

    val indexEdges = ls.flatMap { kv =>
      serDe.indexEdgeDeserializer(label.schemaVersion).fromKeyValues(Seq(kv), None)
    }

    val indexEdge = indexEdges.head

    println(indexEdge)
    println(bulkEdge)

    bulkEdge shouldBe (indexEdge)
  }
  test("test generateKeyValues edge only. LocalBulkLoaderTransformer") {
    val label = initTestEdgeSchema(s2, tableName, schemaVersion, compressionAlgorithm)
    /* end of initialize model */

    val bulkEdgeString = "1416236400000\tinsert\tedge\ta\tb\tfriends\t{\"since\":1316236400000,\"score\":10}"

    val transformerMode = "local"
    val ls = transformToSKeyValues(transformerMode, Seq(bulkEdgeString))

    val serDe = s2.defaultStorage.serDe

    val bulkEdge = s2.elementBuilder.toGraphElement(bulkEdgeString, options.labelMapping).get

    val indexEdges = ls.flatMap { kv =>
      serDe.indexEdgeDeserializer(label.schemaVersion).fromKeyValues(Seq(kv), None)
    }

    val indexEdge = indexEdges.head

    println(indexEdge)
    println(bulkEdge)

    bulkEdge shouldBe (indexEdge)
  }

  test("test generateKeyValues vertex only. SparkBulkLoaderTransformer") {
    val serviceColumn = initTestVertexSchema(s2)
    val bulkVertexString = "20171201\tinsert\tvertex\t800188448586078\tdevice_profile\timei\t{\"first_time\":\"20171025\",\"last_time\":\"20171112\",\"total_active_days\":14,\"query_amount\":1526.0,\"active_months\":2,\"fua\":\"M5+Note\",\"location_often_province\":\"广东省\",\"location_often_city\":\"深圳市\",\"location_often_days\":6,\"location_last_province\":\"广东省\",\"location_last_city\":\"深圳市\",\"fimei_legality\":3}"
    val bulkVertex = s2.elementBuilder.toGraphElement(bulkVertexString, options.labelMapping).get

    val transformerMode = "spark"
    val ls = transformToSKeyValues(transformerMode, Seq(bulkVertexString))

    val serDe = s2.defaultStorage.serDe

    val vertex = serDe.vertexDeserializer(serviceColumn.schemaVersion).fromKeyValues(ls, None).get

    PostProcess.s2VertexToJson(vertex).foreach { jsValue =>
      println(Json.prettyPrint(jsValue))
    }

    bulkVertex shouldBe (vertex)
  }

  test("test generateKeyValues vertex only. LocalBulkLoaderTransformer") {
    val serviceColumn = initTestVertexSchema(s2)
    val bulkVertexString = "20171201\tinsert\tvertex\t800188448586078\tdevice_profile\timei\t{\"first_time\":\"20171025\",\"last_time\":\"20171112\",\"total_active_days\":14,\"query_amount\":1526.0,\"active_months\":2,\"fua\":\"M5+Note\",\"location_often_province\":\"广东省\",\"location_often_city\":\"深圳市\",\"location_often_days\":6,\"location_last_province\":\"广东省\",\"location_last_city\":\"深圳市\",\"fimei_legality\":3}"
    val bulkVertex = s2.elementBuilder.toGraphElement(bulkVertexString, options.labelMapping).get

    val transformerMode = "local"
    val ls = transformToSKeyValues(transformerMode, Seq(bulkVertexString))

    val serDe = s2.defaultStorage.serDe

    val vertex = serDe.vertexDeserializer(serviceColumn.schemaVersion).fromKeyValues(ls, None).get

    PostProcess.s2VertexToJson(vertex).foreach { jsValue =>
      println(Json.prettyPrint(jsValue))
    }

    bulkVertex shouldBe (vertex)
  }

  //   this test case expect options.input already exist with valid bulk load format.
  //  test("bulk load and fetch vertex: spark mode") {
  //    import scala.collection.JavaConverters._
  //    val serviceColumn = initTestVertexSchema(s2)
  //
  //    val bulkVertexLs = Source.fromFile(options.input).getLines().toSeq
  //    val input = sc.parallelize(bulkVertexLs)
  //
  //    HFileGenerator.generate(sc, s2Config, input, options)
  //
  //    val hfileArgs = Array(options.output, options.tableName)
  //    val hbaseConfig = HBaseConfiguration.create()
  //
  //    val ret = ToolRunner.run(hbaseConfig, new LoadIncrementalHFiles(hbaseConfig), hfileArgs)
  //
  //    val s2Vertices = s2.vertices().asScala.toSeq.map(_.asInstanceOf[S2VertexLike])
  //    val json = PostProcess.verticesToJson(s2Vertices)
  //
  //    println(Json.prettyPrint(json))
  //  }

  //   this test case expect options.input already exist with valid bulk load format.
  //  test("bulk load and fetch vertex: mr mode") {
  //    val serviceColumn = initTestVertexSchema(s2)
  //
  //    val bulkVertexLs = Source.fromFile(options.input).getLines().toSeq
  //    val input = sc.parallelize(bulkVertexLs)
  //
  //    HFileMRGenerator.generate(sc, s2Config, input, options)
  //
  //    val hfileArgs = Array(options.output, options.tableName)
  //    val hbaseConfig = HBaseConfiguration.create()
  //
  //    val ret = ToolRunner.run(hbaseConfig, new LoadIncrementalHFiles(hbaseConfig), hfileArgs)
  //    val s2Vertices = s2.vertices().asScala.toSeq.map(_.asInstanceOf[S2VertexLike])
  //    val json = PostProcess.verticesToJson(s2Vertices)
  //
  //    println(Json.prettyPrint(json))
  //  }
}
