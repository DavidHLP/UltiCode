package com.ulticode.recommend.spark

import org.apache.spark.sql.{SparkSession, DataFrame, functions => F}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types.{DoubleType, IntegerType, StringType, StructField, StructType, MapType, ArrayType}

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

/**
 * Spark job for computing user features from submission data.
 *
 * This job reads submission and problem data from Parquet files and computes
 * various user features including:
 * - Activity features: totalSubmissions, recentSubmissions
 * - Skill features: success rates by difficulty
 * - Tag features: tagMastery, tagPreferences
 * - Derived features: skillLevel
 *
 * Usage:
 * {{{
 * spark-submit --class com.ulticode.recommend.spark.OfflineFeatureJob \
 *   recommend-spark.jar \
 *   --input /path/to/submissions \
 *   --problems /path/to/problems \
 *   --output /path/to/output \
 *   --date 2024-01-15
 * }}}
 */
object OfflineFeatureJob {

  /** Result status constants */
  private val RESULT_ACCEPTED = "AC"
  private val RESULT_WRONG_ANSWER = "WA"
  private val RESULT_TIME_LIMIT = "TLE"
  private val RESULT_MEMORY_LIMIT = "MLE"
  private val RESULT_RUNTIME_ERROR = "RE"
  private val RESULT_COMPILE_ERROR = "CE"

  /** Difficulty constants */
  private val DIFFICULTY_EASY = "Easy"
  private val DIFFICULTY_MEDIUM = "Medium"
  private val DIFFICULTY_HARD = "Hard"

  /** Skill level thresholds */
  private val SKILL_LEVEL_BEGINNER = "beginner"
  private val SKILL_LEVEL_INTERMEDIATE = "intermediate"
  private val SKILL_LEVEL_ADVANCED = "advanced"

  /** Recent activity window in days */
  private val RECENT_DAYS_WINDOW = 7

  /** Strong tag mastery threshold */
  private val STRONG_TAG_THRESHOLD = 0.7

  /** Weak tag mastery threshold */
  private val WEAK_TAG_THRESHOLD = 0.3

  /**
   * Main entry point for the Spark job.
   *
   * @param args Command-line arguments
   */
  def main(args: Array[String]): Unit = {
    val params = parseArgs(args)

    val spark = SparkSession.builder()
      .appName(s"${SPARK_APP_NAME}-OfflineFeatureJob")
      .config(SPARK_SQL_SHUFFLE_PARTITIONS, DEFAULT_PARTITIONS)
      .getOrCreate()

    try {
      import spark.implicits._

      // Set log level
      spark.sparkContext.setLogLevel("WARN")

      // Load input data
      val submissions = spark.read.parquet(params.inputPath)
      val problems = spark.read.parquet(params.problemPath)

      // Compute user features
      val userFeatures = computeUserFeatures(submissions, problems, params.referenceDate)

      // Write output
      userFeatures.write
        .mode("overwrite")
        .parquet(params.outputPath)

      println(s"Successfully computed features for ${userFeatures.count()} users")
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
    var problemPath: String = null
    var outputPath: String = null
    var referenceDate: LocalDate = LocalDate.now()

    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--input" =>
          inputPath = args(i + 1)
          i += 2
        case "--problems" =>
          problemPath = args(i + 1)
          i += 2
        case "--output" =>
          outputPath = args(i + 1)
          i += 2
        case "--date" =>
          referenceDate = LocalDate.parse(args(i + 1), DateTimeFormatter.ISO_LOCAL_DATE)
          i += 2
        case _ =>
          i += 1
      }
    }

    require(inputPath != null, "--input is required")
    require(problemPath != null, "--problems is required")
    require(outputPath != null, "--output is required")

    JobParams(inputPath, problemPath, outputPath, referenceDate)
  }

  /**
   * Computes all user features from submission and problem data.
   *
   * @param submissions DataFrame containing submission data
   * @param problems DataFrame containing problem metadata
   * @param referenceDate Reference date for time-based calculations
   * @return DataFrame with computed user features
   */
  def computeUserFeatures(
    submissions: DataFrame,
    problems: DataFrame,
    referenceDate: LocalDate = LocalDate.now()
  ): DataFrame = {
    // Prepare submission data with problem information
    val enrichedSubmissions = enrichSubmissionsWithProblemInfo(submissions, problems)

    // Compute individual feature sets
    val activityFeatures = computeActivityFeatures(enrichedSubmissions, referenceDate)
    val skillFeatures = computeSkillFeatures(enrichedSubmissions)
    val tagFeatures = computeTagFeatures(enrichedSubmissions)

    // Combine all features
    val allFeatures = activityFeatures
      .join(skillFeatures, Seq("userId"), "full_outer")
      .join(tagFeatures, Seq("userId"), "full_outer")

    // Add derived features and finalize schema
    finalizeUserFeatures(allFeatures)
  }

  /**
   * Enriches submission data with problem metadata.
   *
   * @param submissions Raw submission DataFrame
   * @param problems Problem metadata DataFrame
   * @return Enriched DataFrame with problem info
   */
  private def enrichSubmissionsWithProblemInfo(
    submissions: DataFrame,
    problems: DataFrame
  ): DataFrame = {
    // Join submissions with problems to get difficulty and tags
    submissions
      .join(problems, Seq("problemId"), "left")
      // Add isAccepted flag for easier calculations
      .withColumn("isAccepted", F.col("result") === RESULT_ACCEPTED)
  }

  /**
   * Computes activity-related features.
   *
   * Features computed:
   * - totalSubmissions: Total number of submissions
   * - recentSubmissions: Submissions in last 7 days
   * - activityLevel: Normalized activity level (0-1)
   *
   * @param submissions Enriched submission DataFrame
   * @param referenceDate Reference date for recent calculations
   * @return DataFrame with activity features
   */
  def computeActivityFeatures(
    submissions: DataFrame,
    referenceDate: LocalDate = LocalDate.now()
  ): DataFrame = {
    val referenceTimestamp = referenceDate.atStartOfDay()
    val recentThreshold = referenceTimestamp.minusDays(RECENT_DAYS_WINDOW)

    submissions
      .groupBy("userId")
      .agg(
        // Total submissions
        F.count("*").cast(IntegerType).as("totalSubmissions"),
        // Recent submissions (last 7 days)
        F.sum(F.when(
          F.col("timestamp") >= F.lit(recentThreshold), 1
        ).otherwise(0)).cast(IntegerType).as("recentSubmissions"),
        // Min and max timestamps for activity level calculation
        F.min("timestamp").as("firstSubmission"),
        F.max("timestamp").as("lastSubmission")
      )
      // Calculate activity level based on submission frequency
      .withColumn(
        "activityLevel",
        computeActivityLevel(F.col("totalSubmissions"), F.col("recentSubmissions"))
      )
      .select(
        "userId",
        "totalSubmissions",
        "recentSubmissions",
        "activityLevel"
      )
  }

  /**
   * Computes skill-related features based on success rates by difficulty.
   *
   * Features computed:
   * - easySuccessRate: Success rate on Easy problems
   * - mediumSuccessRate: Success rate on Medium problems
   * - hardSuccessRate: Success rate on Hard problems
   * - skillLevel: Derived skill level classification
   *
   * @param submissions Enriched submission DataFrame
   * @return DataFrame with skill features
   */
  def computeSkillFeatures(submissions: DataFrame, problems: DataFrame = null): DataFrame = {
    // If problems is provided, use it; otherwise assume submissions already has difficulty
    val df = if (problems != null) {
      enrichSubmissionsWithProblemInfo(submissions, problems)
    } else {
      submissions
    }

    df
      .groupBy("userId", "difficulty")
      .agg(
        F.count("*").as("totalAttempts"),
        F.sum(F.when(F.col("isAccepted"), 1).otherwise(0)).as("acceptedCount")
      )
      // Calculate success rate per difficulty
      .withColumn(
        "successRate",
        F.when(F.col("totalAttempts") > 0,
          F.col("acceptedCount") / F.col("totalAttempts")
        ).otherwise(0.0)
      )
      // Pivot to get columns per difficulty
      .groupBy("userId")
      .pivot("difficulty", Seq(DIFFICULTY_EASY, DIFFICULTY_MEDIUM, DIFFICULTY_HARD))
      .agg(F.first("successRate"))
      // Fill nulls with 0
      .na.fill(0.0, Seq(DIFFICULTY_EASY, DIFFICULTY_MEDIUM, DIFFICULTY_HARD))
      // Rename columns
      .withColumnRenamed(DIFFICULTY_EASY, "easySuccessRate")
      .withColumnRenamed(DIFFICULTY_MEDIUM, "mediumSuccessRate")
      .withColumnRenamed(DIFFICULTY_HARD, "hardSuccessRate")
      // Add derived skill level
      .withColumn(
        "skillLevel",
        computeSkillLevel(
          F.col("easySuccessRate"),
          F.col("mediumSuccessRate"),
          F.col("hardSuccessRate")
        )
      )
  }

  /**
   * Computes tag-related features.
   *
   * Features computed:
   * - tagMastery: Map of tag -> success rate
   * - tagPreferences: Map of tag -> fraction of attempted problems
   * - strongTags: Tags with mastery > 0.7
   * - weakTags: Tags with mastery < 0.3
   *
   * @param submissions Enriched submission DataFrame
   * @return DataFrame with tag features
   */
  def computeTagFeatures(submissions: DataFrame, problems: DataFrame = null): DataFrame = {
    // If problems is provided, use it; otherwise assume submissions already has tags
    val df = if (problems != null) {
      enrichSubmissionsWithProblemInfo(submissions, problems)
    } else {
      submissions
    }

    // Explode tags array to get one row per tag
    val tagExploded = df
      .filter(F.col("tags").isNotNull)
      .withColumn("tag", F.explode(F.col("tags")))

    // Calculate tag-level statistics
    val tagStats = tagExploded
      .groupBy("userId", "tag")
      .agg(
        F.count("*").as("tagAttempts"),
        F.sum(F.when(F.col("isAccepted"), 1).otherwise(0)).as("tagAccepted")
      )
      .withColumn(
        "tagMasteryScore",
        F.when(F.col("tagAttempts") > 0, F.col("tagAccepted") / F.col("tagAttempts"))
          .otherwise(0.0)
      )

    // Calculate total attempts per user for preference calculation
    val userTotals = df
      .groupBy("userId")
      .agg(F.count("*").as("totalUserAttempts"))

    // Create tag mastery map
    val tagMastery = tagStats
      .groupBy("userId")
      .agg(
        F.map_from_entries(
          F.collect_list(F.struct(F.col("tag"), F.col("tagMasteryScore")))
        ).as("tagMastery")
      )

    // Create tag preferences (fraction of attempts per tag)
    val tagPreferences = tagStats
      .join(userTotals, Seq("userId"))
      .withColumn(
        "tagPreferenceScore",
        F.col("tagAttempts") / F.col("totalUserAttempts")
      )
      .groupBy("userId")
      .agg(
        F.map_from_entries(
          F.collect_list(F.struct(F.col("tag"), F.col("tagPreferenceScore")))
        ).as("tagPreferences")
      )

    // Identify strong and weak tags
    val strongWeakTags = tagStats
      .groupBy("userId")
      .agg(
        F.collect_set(
          F.when(F.col("tagMasteryScore") >= STRONG_TAG_THRESHOLD, F.col("tag"))
        ).as("strongTags"),
        F.collect_set(
          F.when(F.col("tagMasteryScore") < WEAK_TAG_THRESHOLD, F.col("tag"))
        ).as("weakTags")
      )
      // Filter out nulls from collect_set (for tags that don't meet conditions)
      .withColumn("strongTags",
        F.expr("filter(strongTags, x -> x is not null)"))
      .withColumn("weakTags",
        F.expr("filter(weakTags, x -> x is not null)"))

    // Combine all tag features
    tagMastery
      .join(tagPreferences, Seq("userId"), "full_outer")
      .join(strongWeakTags, Seq("userId"), "full_outer")
  }

  /**
   * Finalizes user features by combining all computed features and handling edge cases.
   *
   * @param allFeatures DataFrame with all computed features
   * @return Final DataFrame with complete user features
   */
  private def finalizeUserFeatures(allFeatures: DataFrame): DataFrame = {
    // Define the final schema with proper null handling
    allFeatures
      // Fill nulls for users with missing data
      .na.fill(0, Seq("totalSubmissions", "recentSubmissions"))
      .na.fill(0.0, Seq("activityLevel", "easySuccessRate", "mediumSuccessRate", "hardSuccessRate"))
      .na.fill(SKILL_LEVEL_BEGINNER, Seq("skillLevel"))
      // Handle null maps and arrays
      .withColumn("tagMastery",
        F.coalesce(F.col("tagMastery"), F.map(F.lit(""), F.lit(0.0))))
      .withColumn("tagPreferences",
        F.coalesce(F.col("tagPreferences"), F.map(F.lit(""), F.lit(0.0))))
      .withColumn("strongTags",
        F.coalesce(F.col("strongTags"), F.array()))
      .withColumn("weakTags",
        F.coalesce(F.col("weakTags"), F.array()))
      // Create features struct
      .withColumn("features",
        F.struct(
          F.col("totalSubmissions"),
          F.col("recentSubmissions"),
          F.col("activityLevel"),
          F.col("easySuccessRate"),
          F.col("mediumSuccessRate"),
          F.col("hardSuccessRate"),
          F.col("skillLevel"),
          F.col("tagMastery"),
          F.col("tagPreferences"),
          F.col("strongTags"),
          F.col("weakTags")
        )
      )
      // Select final columns
      .select("userId", "features")
  }

  /**
   * UDF to compute activity level based on submission frequency.
   *
   * Activity level is computed as a weighted combination of:
   * - Recent activity (last 7 days): weight 0.7
   * - Total historical activity: weight 0.3
   *
   * Both are normalized to 0-1 range.
   */
  private def computeActivityLevel =
    F.udf((totalSubmissions: Int, recentSubmissions: Int) => {
      if (totalSubmissions == 0) {
        0.0
      } else {
        // Normalize total submissions (capped at 1000 for 1.0)
        val totalNormalized = math.min(totalSubmissions.toDouble / 1000.0, 1.0)
        // Normalize recent submissions (capped at 50 for 1.0)
        val recentNormalized = math.min(recentSubmissions.toDouble / 50.0, 1.0)
        // Weighted combination
        0.3 * totalNormalized + 0.7 * recentNormalized
      }
    })

  /**
   * UDF to compute skill level based on success rates.
   *
   * Classification rules:
   * - advanced: hardSuccessRate >= 0.5 OR mediumSuccessRate >= 0.7
   * - intermediate: mediumSuccessRate >= 0.4 OR easySuccessRate >= 0.8
   * - beginner: otherwise
   */
  private def computeSkillLevel =
    F.udf((easyRate: Double, mediumRate: Double, hardRate: Double) => {
      if (hardRate >= 0.5 || mediumRate >= 0.7) {
        SKILL_LEVEL_ADVANCED
      } else if (mediumRate >= 0.4 || easyRate >= 0.8) {
        SKILL_LEVEL_INTERMEDIATE
      } else {
        SKILL_LEVEL_BEGINNER
      }
    })

  /**
   * Job parameters case class.
   *
   * @param inputPath Path to submission data
   * @param problemPath Path to problem metadata
   * @param outputPath Path for output features
   * @param referenceDate Reference date for calculations
   */
  private case class JobParams(
    inputPath: String,
    problemPath: String,
    outputPath: String,
    referenceDate: LocalDate
  )
}
