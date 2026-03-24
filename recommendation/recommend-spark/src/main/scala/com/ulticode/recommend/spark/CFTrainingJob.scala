package com.ulticode.recommend.spark

import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.apache.spark.sql.types.{DoubleType, IntegerType, LongType}

/**
 * Spark job for training collaborative filtering model using ALS (Alternating Least Squares).
 *
 * This job reads user submission data from Parquet files and trains a collaborative
 * filtering model using Spark MLlib's ALS algorithm. The model learns latent factors
 * for users and problems based on submission results treated as implicit feedback.
 *
 * Rating mapping from submission results:
 * - AC (Accepted): 1.0
 * - WA (Wrong Answer): 0.3
 * - TLE (Time Limit Exceeded): 0.5
 * - Others (MLE, RE, CE, etc.): 0.1
 *
 * Usage:
 * {{{
 * spark-submit --class com.ulticode.recommend.spark.CFTrainingJob \
 *   recommend-spark.jar \
 *   --input /path/to/submissions \
 *   --output /path/to/model \
 *   --rank 10 \
 *   --maxIter 10
 * }}}
 */
object CFTrainingJob {

  /** Result status constants for rating mapping */
  private val RESULT_ACCEPTED = "AC"
  private val RESULT_WRONG_ANSWER = "WA"
  private val RESULT_TIME_LIMIT = "TLE"

  /** Rating values for different result types */
  private val RATING_AC = 1.0
  private val RATING_WA = 0.3
  private val RATING_TLE = 0.5
  private val RATING_OTHER = 0.1

  /** Default train/test split ratio */
  private val DEFAULT_TRAIN_RATIO = 0.8

  /** Random seed for reproducibility */
  private val RANDOM_SEED = 42L

  /**
   * Main entry point for the Spark job.
   *
   * @param args Command-line arguments
   */
  def main(args: Array[String]): Unit = {
    val params = parseArgs(args)

    val spark = SparkSession.builder()
      .appName(s"${SPARK_APP_NAME}-CFTrainingJob")
      .config(SPARK_SQL_SHUFFLE_PARTITIONS, DEFAULT_PARTITIONS)
      .getOrCreate()

    try {
      import spark.implicits._

      // Set log level
      spark.sparkContext.setLogLevel("WARN")

      println(s"Loading submission data from: ${params.inputPath}")
      val submissions = spark.read.parquet(params.inputPath)

      // Prepare ratings from submissions
      println("Preparing ratings from submissions...")
      val ratings = prepareRatings(submissions)

      // Cache ratings as they will be used multiple times
      ratings.cache()
      val ratingCount = ratings.count()
      println(s"Total ratings prepared: $ratingCount")

      // Split into training and test sets
      val Array(trainData, testData) = ratings.randomSplit(
        Array(DEFAULT_TRAIN_RATIO, 1.0 - DEFAULT_TRAIN_RATIO),
        RANDOM_SEED
      )

      println(s"Training set size: ${trainData.count()}")
      println(s"Test set size: ${testData.count()}")

      // Train the model
      println(s"Training ALS model with params: ${params.alsParams}")
      val model = trainModel(trainData, params.alsParams)

      // Evaluate the model
      println("Evaluating model on test set...")
      val rmse = evaluateModel(model, testData)
      println(s"Test RMSE: $rmse")

      // Save the model
      val modelPath = s"${params.outputPath}/model"
      println(s"Saving model to: $modelPath")
      model.write.overwrite().save(modelPath)

      // Export factors
      println("Exporting user and item factors...")
      exportFactors(model, params.outputPath)

      // Save model metrics
      saveModelMetrics(spark, rmse, params, ratingCount, params.outputPath)

      println(s"Training completed successfully!")
      println(s"Model and factors saved to: ${params.outputPath}")

    } finally {
      spark.stop()
    }
  }

  /**
   * Parses command-line arguments.
   *
   * @param args Command-line arguments array
   * @return Parsed job parameters
   */
  private def parseArgs(args: Array[String]): JobParams = {
    var inputPath: String = null
    var outputPath: String = null
    var rank: Int = 10
    var maxIter: Int = 10
    var regParam: Double = 0.1
    var alpha: Double = 1.0
    var implicitPrefs: Boolean = true

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--input" =>
          inputPath = args(i + 1)
          i += 2
        case "--output" =>
          outputPath = args(i + 1)
          i += 2
        case "--rank" =>
          rank = args(i + 1).toInt
          i += 2
        case "--maxIter" =>
          maxIter = args(i + 1).toInt
          i += 2
        case "--regParam" =>
          regParam = args(i + 1).toDouble
          i += 2
        case "--alpha" =>
          alpha = args(i + 1).toDouble
          i += 2
        case "--explicit" =>
          implicitPrefs = false
          i += 1
        case _ =>
          i += 1
      }
    }

    require(inputPath != null, "--input is required")
    require(outputPath != null, "--output is required")
    require(rank > 0, s"--rank must be positive, got: $rank")
    require(maxIter > 0, s"--maxIter must be positive, got: $maxIter")
    require(regParam > 0, s"--regParam must be positive, got: $regParam")
    require(alpha > 0, s"--alpha must be positive, got: $alpha")

    val alsParams = ALSParams(
      rank = rank,
      maxIter = maxIter,
      regParam = regParam,
      alpha = alpha,
      implicitPrefs = implicitPrefs
    )

    JobParams(inputPath, outputPath, alsParams)
  }

  /**
   * Prepares ratings DataFrame from submission data.
   *
   * Maps submission results to implicit ratings:
   * - AC -> 1.0
   * - WA -> 0.3
   * - TLE -> 0.5
   * - Others -> 0.1
   *
   * @param submissions DataFrame containing submission data with userId, problemId, result columns
   * @return DataFrame with columns: userId (int), problemId (int), rating (double)
   */
  def prepareRatings(submissions: DataFrame): DataFrame = {
    submissions
      // Map result to rating
      .withColumn("rating",
        F.when(F.col("result") === RESULT_ACCEPTED, RATING_AC)
          .when(F.col("result") === RESULT_WRONG_ANSWER, RATING_WA)
          .when(F.col("result") === RESULT_TIME_LIMIT, RATING_TLE)
          .otherwise(RATING_OTHER)
      )
      // Cast userId and problemId to integers (required by ALS)
      .withColumn("userId", F.col("userId").cast(IntegerType))
      .withColumn("problemId", F.col("problemId").cast(IntegerType))
      // Select required columns
      .select("userId", "problemId", "rating")
      // Remove any null values
      .na.drop()
  }

  /**
   * Trains an ALS model using the provided ratings and parameters.
   *
   * @param ratings DataFrame with columns: userId, problemId, rating
   * @param params ALS hyperparameters
   * @return Trained ALSModel
   */
  def trainModel(ratings: DataFrame, params: ALSParams): ALSModel = {
    val als = new ALS()
      .setUserCol("userId")
      .setItemCol("problemId")
      .setRatingCol("rating")
      .setRank(params.rank)
      .setMaxIter(params.maxIter)
      .setRegParam(params.regParam)
      .setAlpha(params.alpha)
      .setImplicitPrefs(params.implicitPrefs)
      .setColdStartStrategy("drop")
      .setNonnegative(true)
      .setSeed(RANDOM_SEED)

    als.fit(ratings)
  }

  /**
   * Evaluates the model on test data and returns RMSE.
   *
   * @param model Trained ALS model
   * @param testRatings DataFrame with test ratings
   * @return RMSE value on test set
   */
  def evaluateModel(model: ALSModel, testRatings: DataFrame): Double = {
    val predictions = model.transform(testRatings)

    // Filter out NaN predictions (cold start items)
    val validPredictions = predictions.filter(
      F.col("prediction").isNotNull && !F.isnan(F.col("prediction"))
    )

    if (validPredictions.count() == 0) {
      // Return a high RMSE if no valid predictions
      return Double.MaxValue
    }

    val evaluator = new RegressionEvaluator()
      .setMetricName("rmse")
      .setLabelCol("rating")
      .setPredictionCol("prediction")

    evaluator.evaluate(validPredictions)
  }

  /**
   * Exports user and item factors from the trained model.
   *
   * Creates two Parquet files:
   * - userFactors: userId, features (array of doubles)
   * - itemFactors: problemId, features (array of doubles)
   *
   * @param model Trained ALS model
   * @param outputPath Base output path
   */
  def exportFactors(model: ALSModel, outputPath: String): Unit = {
    // Export user factors
    val userFactors = model.userFactors
      .withColumnRenamed("id", "userId")
      .withColumnRenamed("features", "features")

    userFactors.write
      .mode("overwrite")
      .parquet(s"$outputPath/userFactors")

    println(s"User factors saved to: $outputPath/userFactors")

    // Export item (problem) factors
    val itemFactors = model.itemFactors
      .withColumnRenamed("id", "problemId")
      .withColumnRenamed("features", "features")

    itemFactors.write
      .mode("overwrite")
      .parquet(s"$outputPath/itemFactors")

    println(s"Item factors saved to: $outputPath/itemFactors")
  }

  /**
   * Saves model metrics to a Parquet file for tracking.
   *
   * @param spark SparkSession
   * @param rmse RMSE value
   * @param params Job parameters used for training
   * @param ratingCount Total number of ratings
   * @param outputPath Base output path
   */
  private def saveModelMetrics(
    spark: SparkSession,
    rmse: Double,
    params: JobParams,
    ratingCount: Long,
    outputPath: String
  ): Unit = {
    import spark.implicits._

    val metrics = Seq((
      java.time.LocalDateTime.now().toString,
      rmse,
      params.alsParams.rank,
      params.alsParams.maxIter,
      params.alsParams.regParam,
      params.alsParams.alpha,
      params.alsParams.implicitPrefs,
      ratingCount
    )).toDF(
      "timestamp",
      "rmse",
      "rank",
      "maxIter",
      "regParam",
      "alpha",
      "implicitPrefs",
      "ratingCount"
    )

    metrics.write
      .mode("overwrite")
      .parquet(s"$outputPath/metrics")

    println(s"Model metrics saved to: $outputPath/metrics")
  }

  /**
   * Loads a previously trained ALS model from disk.
   *
   * @param spark SparkSession
   * @param modelPath Path to the saved model
   * @return Loaded ALSModel
   */
  def loadModel(spark: SparkSession, modelPath: String): ALSModel = {
    ALSModel.load(modelPath)
  }

  /**
   * Generates recommendations for all users.
   *
   * @param model Trained ALS model
   * @param numRecommendations Number of recommendations per user
   * @return DataFrame with userId and recommendations (array of problemIds)
   */
  def recommendForAllUsers(model: ALSModel, numRecommendations: Int): DataFrame = {
    model.recommendForAllUsers(numRecommendations)
  }

  /**
   * Generates recommendations for specific users.
   *
   * @param model Trained ALS model
   * @param users DataFrame with userId column
   * @param numRecommendations Number of recommendations per user
   * @return DataFrame with userId and recommendations
   */
  def recommendForUsers(
    model: ALSModel,
    users: DataFrame,
    numRecommendations: Int
  ): DataFrame = {
    model.recommendForUserSubset(users, numRecommendations)
  }

  /**
   * ALS hyperparameters case class.
   *
   * @param rank Number of latent factors (default: 10)
   * @param maxIter Maximum number of iterations (default: 10)
   * @param regParam Regularization parameter (default: 0.1)
   * @param alpha Alpha parameter for implicit feedback (default: 1.0)
   * @param implicitPrefs Whether to use implicit feedback mode (default: true)
   */
  case class ALSParams(
    rank: Int = 10,
    maxIter: Int = 10,
    regParam: Double = 0.1,
    alpha: Double = 1.0,
    implicitPrefs: Boolean = true
  ) {
    override def toString: String = {
      s"ALSParams(rank=$rank, maxIter=$maxIter, regParam=$regParam, alpha=$alpha, implicitPrefs=$implicitPrefs)"
    }
  }

  /**
   * Job parameters case class.
   *
   * @param inputPath Path to submission data
   * @param outputPath Path for output model and factors
   * @param alsParams ALS hyperparameters
   */
  private case class JobParams(
    inputPath: String,
    outputPath: String,
    alsParams: ALSParams
  )
}
