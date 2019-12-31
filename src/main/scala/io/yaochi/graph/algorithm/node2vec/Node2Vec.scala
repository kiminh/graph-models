package io.yaochi.graph.algorithm.node2vec

import io.yaochi.graph.algorithm.base.GNN
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset

class Node2Vec extends GNN[Node2VecPSModel] {

  override def makeModel(minId: Long, maxId: Long, index: RDD[Long]): Node2VecPSModel = ???

  override def makeGraph(edges: RDD[(Long, Long)], model: Node2VecPSModel): Dataset[_] = ???

  override def fit(model: Node2VecPSModel, graph: Dataset[_]): Unit = {

  }

  override def copy(extra: ParamMap): Transformer = defaultCopy(extra)
}
