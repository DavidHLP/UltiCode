package com.ulticode.recommend

/**
 * Package object for Spark-based recommendation utilities and configuration.
 */
package object spark {

  // ===========================================
  // Spark Configuration Constants
  // ===========================================

  /** Application name for Spark jobs */
  val SPARK_APP_NAME: String = "UltiCode-Recommend-Spark"

  /** Default master URL for local development */
  val SPARK_MASTER_LOCAL: String = "local[*]"

  /** Default number of partitions for RDDs */
  val DEFAULT_PARTITIONS: Int = 200

  /** Spark SQL shuffle partitions */
  val SPARK_SQL_SHUFFLE_PARTITIONS: String = "spark.sql.shuffle.partitions"

  // ===========================================
  // Recommendation Configuration Constants
  // ===========================================

  /** Default number of recommendations to generate per user */
  val DEFAULT_TOP_K_RECOMMENDATIONS: Int = 100

  /** Minimum similarity score threshold for recommendations */
  val MIN_SIMILARITY_THRESHOLD: Double = 0.1

  /** Maximum number of features to use in similarity computation */
  val MAX_FEATURES: Int = 1000

  // ===========================================
  // Data Format Constants
  // ===========================================

  /** Parquet compression codec */
  val PARQUET_COMPRESSION: String = "snappy"

  /** Default batch size for processing */
  val DEFAULT_BATCH_SIZE: Int = 10000

  // ===========================================
  // Utility Methods
  // ===========================================

  /**
   * Creates a map of default Spark configuration options.
   *
   * @return Map of configuration key-value pairs
   */
  def defaultSparkConfig: Map[String, String] = Map(
    SPARK_SQL_SHUFFLE_PARTITIONS -> DEFAULT_PARTITIONS.toString,
    "spark.sql.parquet.compression.codec" -> PARQUET_COMPRESSION,
    "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
    "spark.kryoserializer.buffer.max" -> "512m"
  )

  /**
   * Validates that a similarity score is within valid range [0, 1].
   *
   * @param score The similarity score to validate
   * @return True if the score is valid, false otherwise
   */
  def isValidSimilarityScore(score: Double): Boolean = {
    score >= 0.0 && score <= 1.0
  }

  /**
   * Normalizes a raw score to a value between 0 and 1.
   *
   * @param rawScore The raw score to normalize
   * @param minScore The minimum possible score
   * @param maxScore The maximum possible score
   * @return The normalized score between 0 and 1
   */
  def normalizeScore(rawScore: Double, minScore: Double, maxScore: Double): Double = {
    if (maxScore == minScore) {
      0.5
    } else {
      val normalized = (rawScore - minScore) / (maxScore - minScore)
      Math.max(0.0, Math.min(1.0, normalized))
    }
  }

  /**
   * Calculates the number of partitions based on data size.
   *
   * @param recordCount The number of records
   * @param recordsPerPartition Target number of records per partition
   * @return Recommended number of partitions
   */
  def calculatePartitions(recordCount: Long, recordsPerPartition: Int = DEFAULT_BATCH_SIZE): Int = {
    val partitions = Math.ceil(recordCount.toDouble / recordsPerPartition).toInt
    Math.max(1, Math.min(partitions, DEFAULT_PARTITIONS))
  }
}
