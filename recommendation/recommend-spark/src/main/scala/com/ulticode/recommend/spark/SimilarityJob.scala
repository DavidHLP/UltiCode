package com.ulticode.recommend.spark

import org.apache.spark.sql.{SparkSession, DataFrame, Dataset, Row, functions => F}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types.{DoubleType, IntegerType, LongType, StringType, StructField, StructType, ArrayType}

/**
 * Spark job for computing problem similarity using Jaccard similarity on tags.
 *
 * This job reads problem metadata from Parquet files and computes pairwise
 * similarity between problems based on their tags using Jaccard similarity.
 *
 * Jaccard Similarity Formula: |A ∩ B| / |A ∪ B|
 *
 * Usage:
 * {{{
 * spark-submit --class com.ulticode.recommend.spark.SimilarityJob \
 *   recommend-spark.jar \
 *   --input /path/to/problems \
 *   --output /path/to/similarity \
 *   --threshold 0.3 \
 *   --topK 50
 * }}}
 */
object SimilarityJob {

  /** Default similarity threshold */
  private val DEFAULT_SIMILARITY_THRESHOLD = 0.3

  /** Default number of similar problems to keep per problem */
  private val DEFAULT_TOP_K = 50

  /**
   * Main entry point for the Spark job.
   *
   * @param args Command-line arguments
   */
  def main(args: Array[String]): Unit = {
    val params = parseArgs(args)

    val spark = SparkSession.builder()
      .appName(s"${SPARK_APP_NAME}-SimilarityJob")
      .config(SPARK_SQL_SHUFFLE_PARTITIONS, DEFAULT_PARTITIONS)
      .getOrCreate()

    try {
      import spark.implicits._

      // Set log level
      spark.sparkContext.setLogLevel("WARN")

      // Load input data
      val problems = spark.read.parquet(params.inputPath)

      // Compute similarity matrix
      val similarity = computeSimilarity(problems, params.threshold)

      // Get top K similar problems per problem
      val topSimilarities = getTopSimilarProblems(similarity, params.topK)

      // Write output partitioned by problemId for fast lookup
      topSimilarities.write
        .mode("overwrite")
        .partitionBy("problemId1")
        .parquet(params.outputPath)

      println(s"Successfully computed similarity for ${topSimilarities.count()} pairs")
      println(s"Output written to: ${params.outputPath}")

    } finally {
      spark.stop()
    }
  }

  /**
   * Parses command-line arguments.
   *
   * @param args Command-line arguments array
   * @return Parsed parameters
   */
  private def parseArgs(args: Array[String]): JobParams = {
    var inputPath: String = null
    var outputPath: String = null
    var threshold: Double = DEFAULT_SIMILARITY_THRESHOLD
    var topK: Int = DEFAULT_TOP_K

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--input" =>
          inputPath = args(i + 1)
          i += 2
        case "--output" =>
          outputPath = args(i + 1)
          i += 2
        case "--threshold" =>
          threshold = args(i + 1).toDouble
          i += 2
        case "--topK" =>
          topK = args(i + 1).toInt
          i += 2
        case _ =>
          i += 1
      }
    }

    require(inputPath != null, "--input is required")
    require(outputPath != null, "--output is required")
    require(threshold >= 0.0 && threshold <= 1.0, s"--threshold must be between 0 and 1, got: $threshold")
    require(topK > 0, s"--topK must be positive, got: $topK")

    JobParams(inputPath, outputPath, threshold, topK)
  }

  /**
   * Computes pairwise similarity between problems using Jaccard similarity on tags.
   *
   * This method performs a self-join to compute all pairs of problems and
   * calculates their Jaccard similarity based on overlapping tags.
   *
   * @param problems DataFrame containing problem metadata with problemId and tags columns
   * @param threshold Minimum similarity threshold (only pairs above this are kept)
   * @return DataFrame with columns: problemId1, problemId2, similarity
   */
  def computeSimilarity(problems: DataFrame, threshold: Double): DataFrame = {
    import problems.sparkSession.implicits._

    // Filter problems with valid tags
    val problemsWithTags = problems
      .filter(F.col("tags").isNotNull && F.size(F.col("tags")) > 0)
      .select("problemId", "tags")
      .cache()

    // Rename columns for self-join
    val p1 = problemsWithTags
      .withColumnRenamed("problemId", "problemId1")
      .withColumnRenamed("tags", "tags1")

    val p2 = problemsWithTags
      .withColumnRenamed("problemId", "problemId2")
      .withColumnRenamed("tags", "tags2")

    // Perform cross join and compute similarity
    val similarities = p1.crossJoin(p2)
      // Filter to avoid duplicate pairs and self-similarity
      .filter(F.col("problemId1") < F.col("problemId2"))
      // Compute Jaccard similarity using UDF
      .withColumn(
        "similarity",
        jaccardSimilarityUDF(F.col("tags1"), F.col("tags2"))
      )
      // Filter by threshold early to reduce data
      .filter(F.col("similarity") >= threshold)
      .select("problemId1", "problemId2", "similarity")

    problemsWithTags.unpersist()

    similarities
  }

  /**
   * Computes Jaccard similarity between two sets of tags.
   *
   * Jaccard Similarity = |A ∩ B| / |A ∪ B|
   *
   * @param tags1 First set of tags
   * @param tags2 Second set of tags
   * @return Jaccard similarity score between 0 and 1
   */
  def jaccardSimilarity(tags1: Seq[String], tags2: Seq[String]): Double = {
    if (tags1 == null || tags2 == null || tags1.isEmpty || tags2.isEmpty) {
      return 0.0
    }

    val set1 = tags1.toSet
    val set2 = tags2.toSet

    val intersection = set1.intersect(set2).size
    val union = set1.union(set2).size

    if (union == 0) {
      0.0
    } else {
      intersection.toDouble / union.toDouble
    }
  }

  /**
   * UDF wrapper for Jaccard similarity computation.
   */
  private def jaccardSimilarityUDF = F.udf((tags1: Seq[String], tags2: Seq[String]) => {
    jaccardSimilarity(tags1, tags2)
  })

  /**
   * Gets the top K most similar problems for a specific problem.
   *
   * @param similarity DataFrame with columns: problemId1, problemId2, similarity
   * @param problemId The problem ID to get similar problems for
   * @param k Number of similar problems to return
   * @return DataFrame with columns: problemId, similarProblemId, similarity
   */
  def getTopSimilarProblems(similarity: DataFrame, problemId: Long, k: Int): DataFrame = {
    import similarity.sparkSession.implicits._

    // Get all pairs involving this problem
    val problemPairs = similarity
      .filter(F.col("problemId1") === problemId || F.col("problemId2") === problemId)
      // Normalize to always have the target problem in problemId1
      .withColumn(
        "similarProblemId",
        F.when(F.col("problemId1") === problemId, F.col("problemId2"))
          .otherwise(F.col("problemId1"))
      )
      .withColumn("problemId", F.lit(problemId))
      .select("problemId", "similarProblemId", "similarity")

    // Rank and get top K
    val window = Window
      .partitionBy("problemId")
      .orderBy(F.col("similarity").desc)

    problemPairs
      .withColumn("rank", F.row_number().over(window))
      .filter(F.col("rank") <= k)
      .drop("rank")
  }

  /**
   * Gets the top K most similar problems for all problems.
   *
   * @param similarity DataFrame with columns: problemId1, problemId2, similarity
   * @param k Number of similar problems to keep per problem
   * @return DataFrame with columns: problemId1, problemId2, similarity
   */
  def getTopSimilarProblems(similarity: DataFrame, k: Int): DataFrame = {
    // Create union of (problemId1, problemId2, similarity) and (problemId2, problemId1, similarity)
    // to have entries for both directions
    val forward = similarity.select(
      F.col("problemId1"),
      F.col("problemId2"),
      F.col("similarity")
    )

    val reverse = similarity.select(
      F.col("problemId2").as("problemId1"),
      F.col("problemId1").as("problemId2"),
      F.col("similarity")
    )

    val allPairs = forward.union(reverse)

    // Rank by similarity within each problemId1 group
    val window = Window
      .partitionBy("problemId1")
      .orderBy(F.col("similarity").desc)

    allPairs
      .withColumn("rank", F.row_number().over(window))
      .filter(F.col("rank") <= k)
      .drop("rank")
  }

  /**
   * Optimized version of similarity computation using MinHash for approximate similarity.
   *
   * This is useful for large datasets where exact pairwise computation is too expensive.
   * Uses Spark MLlib's MinHashLSH for approximate nearest neighbor search.
   *
   * @param problems DataFrame containing problem metadata with problemId and tags columns
   * @param threshold Minimum similarity threshold
   * @param numHashTables Number of hash tables for MinHash (default: 5)
   * @return DataFrame with columns: problemId1, problemId2, similarity
   */
  def computeSimilarityApprox(
    problems: DataFrame,
    threshold: Double,
    numHashTables: Int = 5
  ): DataFrame = {
    import problems.sparkSession.implicits._

    // This is a placeholder for approximate similarity using MinHash
    // For exact computation, use computeSimilarity method
    // MinHashLSH would be used here for large-scale approximate matching

    // For now, fall back to exact computation
    computeSimilarity(problems, threshold)
  }

  /**
   * Job parameters case class.
   *
   * @param inputPath Path to problem metadata
   * @param outputPath Path for output similarity matrix
   * @param threshold Minimum similarity threshold
   * @param topK Number of similar problems to keep per problem
   */
  private case class JobParams(
    inputPath: String,
    outputPath: String,
    threshold: Double,
    topK: Int
  )
}
