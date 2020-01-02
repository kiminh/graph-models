package io.yaochi.graph.algorithm.gcn

import io.yaochi.graph.algorithm.base.{GNN, GraphAdjPartition}
import io.yaochi.graph.params.HasTestRatio
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}

class GCN extends GNN[GCNPSModel] with HasTestRatio {

  override def makeModel(minId: Long, maxId: Long, index: RDD[Long]): GCNPSModel = {
    GCNPSModel.apply(minId, maxId + 1, 0, getOptimizer,
      index, $(psPartitionNum), $(useBalancePartition))
  }

  override def makeGraph(edges: RDD[(Long, Long)], model: GCNPSModel): Dataset[_] = {
    // build adj graph partitions
    val adjGraph = edges.groupByKey($(partitionNum))
      .mapPartitionsWithIndex((index, it) =>
        Iterator.single(GraphAdjPartition.apply(index, it)))

    adjGraph.persist($(storageLevel))
    adjGraph.foreachPartition(_ => Unit)
    adjGraph.map(_.init(model, $(numBatchInit))).reduce(_ + _)

    // build GCN graph partitions
    val gcnGraph = adjGraph.map(GCNPartition(_, model, $(testRatio)))
    gcnGraph.persist($(storageLevel))
    gcnGraph.count()
    adjGraph.unpersist(true)

    implicit val encoder = org.apache.spark.sql.Encoders.kryo[GCNPartition]
    SparkSession.builder().getOrCreate().createDataset(gcnGraph)
  }

  override def fit(model: GCNPSModel, graph: Dataset[_]): Unit = {
    val optim = getOptimizer

    val (trainSize, testSize) = graph.rdd.map(_.asInstanceOf[GCNPartition].getTrainTestSize())
      .reduce((f1, f2) => (f1._1 + f2._1, f1._2 + f2._2))
    println(s"numTrain=$trainSize numTest=$testSize testRatio=${$(testRatio)} samples=${$(numSamples)}")

    for (curEpoch <- 1 to $(numEpoch)) {
      val (lossSum, trainRight) = graph.rdd.map(_.asInstanceOf[GCNPartition].trainEpoch(curEpoch, $(batchSize), model,
        $(featureDim), optim, $(numSamples))).reduce((f1, f2) => (f1._1 + f2._1, f1._2 + f2._2))
      val predRight = graph.rdd.map(_.asInstanceOf[GCNPartition].predictEpoch(curEpoch, $(batchSize) * 10, model,
        $(featureDim), $(numSamples))).reduce(_ + _)
      println(s"curEpoch=$curEpoch " +
        s"train loss=${lossSum / trainSize} " +
        s"train acc=${trainRight.toDouble / trainSize} " +
        s"test acc=${predRight.toDouble / testSize}")
    }
  }

  override def copy(extra: ParamMap): Transformer = defaultCopy(extra)
}