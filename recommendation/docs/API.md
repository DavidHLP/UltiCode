# UltiCode Recommendation System - API Documentation

> REST API for the distributed programming problem recommendation system

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication](#2-authentication)
3. [Endpoints](#3-endpoints)
4. [Request/Response Examples](#4-requestresponse-examples)
5. [Recommendation Scenarios](#5-recommendation-scenarios)
6. [Error Handling](#6-error-handling)
7. [Rate Limiting](#7-rate-limiting)
8. [SDK Examples](#8-sdk-examples)

---

## 1. Overview

### 1.1 Base URL

| Environment | Base URL |
|-------------|----------|
| Development | `http://localhost:8080` |
| Production | `https://api.ulticode.com` |

### 1.2 Content Type

All API requests and responses use JSON format:

```
Content-Type: application/json
```

### 1.3 API Versioning

The current API version is `v1`. Version is included in request headers:

```
Accept: application/json
X-API-Version: 1.0
```

---

## 2. Authentication

Currently, the API does not require authentication for development purposes.

**Note:** Authentication will be added in future versions using JWT tokens.

---

## 3. Endpoints

### 3.1 Get Recommendations

Returns personalized problem recommendations based on user profile and specified scenario.

**Endpoint:**
```
POST /api/recommend
```

**Request Headers:**
| Header | Value | Required |
|--------|-------|----------|
| Content-Type | application/json | Yes |

**Request Body:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| userId | string | Yes | - | Unique user identifier |
| size | integer | No | 10 | Number of recommendations (1-100) |
| scenario | string | No | DAILY | Recommendation scenario |
| sourceProblemId | long | No | null | Source problem ID (required for SIMILAR scenario) |
| targetTags | array[string] | No | null | Filter by specific tags |
| includeSolved | boolean | No | false | Include already solved problems |

**Response Body:**

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Request success status |
| code | integer | Response code (0 = success) |
| message | string | Human-readable message |
| data | object | Recommendation result data |
| data.items | array | List of recommended items |
| data.totalCount | integer | Total available recommendations |
| data.scenario | string | Scenario used for recommendations |
| data.generatedAt | string | ISO 8601 timestamp |

**RecommendItem Structure:**

| Field | Type | Description |
|-------|------|-------------|
| problemId | long | Unique problem identifier |
| slug | string | URL-friendly problem identifier |
| title | string | Problem display title |
| difficulty | string | Difficulty level (Easy/Medium/Hard) |
| score | double | Recommendation score (0.0-1.0) |
| tags | array[string] | Associated tags |
| reason | string | Human-readable recommendation reason |

---

### 3.2 Health Check

Returns the health status of the recommendation service.

**Endpoint:**
```
GET /api/recommend/health
```

**Response Body:**

| Field | Type | Description |
|-------|------|-------------|
| status | string | Service status (UP/DOWN) |
| timestamp | string | Current server timestamp |

---

## 4. Request/Response Examples

### 4.1 Basic Recommendation Request

**Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "size": 10,
    "scenario": "DAILY",
    "includeSolved": false
  }'
```

**Response:**
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "items": [
      {
        "problemId": 1,
        "slug": "two-sum",
        "title": "Two Sum",
        "difficulty": "Easy",
        "score": 0.95,
        "tags": ["array", "hash-table"],
        "reason": "Recommended based on your learning history"
      },
      {
        "problemId": 2,
        "slug": "add-two-numbers",
        "title": "Add Two Numbers",
        "difficulty": "Medium",
        "score": 0.88,
        "tags": ["linked-list", "math"],
        "reason": "Matches your skill level"
      },
      {
        "problemId": 3,
        "slug": "longest-substring-without-repeating-characters",
        "title": "Longest Substring Without Repeating Characters",
        "difficulty": "Medium",
        "score": 0.85,
        "tags": ["string", "sliding-window"],
        "reason": "Strengthen your string manipulation skills"
      }
    ],
    "totalCount": 3,
    "scenario": "DAILY",
    "generatedAt": "2026-03-14T10:30:00"
  }
}
```

### 4.2 Similar Problems Request

**Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-456",
    "size": 5,
    "scenario": "SIMILAR",
    "sourceProblemId": 42
  }'
```

**Response:**
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "items": [
      {
        "problemId": 101,
        "slug": "similar-problem-1",
        "title": "Similar Problem 1",
        "difficulty": "Medium",
        "score": 0.89,
        "tags": ["dynamic-programming", "array"],
        "reason": "Similar to the problem you just solved"
      },
      {
        "problemId": 102,
        "slug": "similar-problem-2",
        "title": "Similar Problem 2",
        "difficulty": "Hard",
        "score": 0.85,
        "tags": ["dynamic-programming", "optimization"],
        "reason": "Builds on concepts from the source problem"
      }
    ],
    "totalCount": 2,
    "scenario": "SIMILAR",
    "generatedAt": "2026-03-14T10:31:00"
  }
}
```

### 4.3 Weak Point Strengthening Request

**Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-789",
    "size": 5,
    "scenario": "WEAK_POINT"
  }'
```

**Response:**
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "items": [
      {
        "problemId": 301,
        "slug": "weak-point-exercise",
        "title": "Weak Point Exercise",
        "difficulty": "Easy",
        "score": 0.90,
        "tags": ["graph", "bfs"],
        "reason": "Focus on your weak area: Graph traversal"
      }
    ],
    "totalCount": 1,
    "scenario": "WEAK_POINT",
    "generatedAt": "2026-03-14T10:32:00"
  }
}
```

### 4.4 Challenge Mode Request

**Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-999",
    "size": 5,
    "scenario": "CHALLENGE"
  }'
```

**Response:**
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "items": [
      {
        "problemId": 201,
        "slug": "challenge-problem-1",
        "title": "Challenge Problem 1",
        "difficulty": "Hard",
        "score": 0.78,
        "tags": ["dynamic-programming", "optimization"],
        "reason": "Push your limits with this challenging problem"
      },
      {
        "problemId": 202,
        "slug": "challenge-problem-2",
        "title": "Challenge Problem 2",
        "difficulty": "Hard",
        "score": 0.75,
        "tags": ["graph", "advanced-algorithm"],
        "reason": "Test your advanced problem-solving skills"
      }
    ],
    "totalCount": 2,
    "scenario": "CHALLENGE",
    "generatedAt": "2026-03-14T10:33:00"
  }
}
```

### 4.5 Request with Target Tags

**Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-111",
    "size": 5,
    "scenario": "DAILY",
    "targetTags": ["array", "two-pointers"]
  }'
```

**Response:**
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "items": [
      {
        "problemId": 501,
        "slug": "two-sum-ii",
        "title": "Two Sum II - Input Array Is Sorted",
        "difficulty": "Medium",
        "score": 0.91,
        "tags": ["array", "two-pointers", "binary-search"],
        "reason": "Matches your selected tags"
      }
    ],
    "totalCount": 1,
    "scenario": "DAILY",
    "generatedAt": "2026-03-14T10:34:00"
  }
}
```

### 4.6 Health Check Request

**Request:**
```bash
curl -X GET http://localhost:8080/api/recommend/health
```

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2026-03-14T10:35:00"
}
```

---

## 5. Recommendation Scenarios

### 5.1 Scenario Overview

| Scenario | Code | Description | Use Case |
|----------|------|-------------|----------|
| Daily Practice | `DAILY` | General recommendations based on user history | Daily practice sessions |
| Similar Problems | `SIMILAR` | Find problems similar to a specific problem | After solving a problem |
| Weak Point Strengthening | `WEAK_POINT` | Focus on user's weak areas | Targeted practice |
| Challenge Mode | `CHALLENGE` | Harder problems to push limits | Skill advancement |

### 5.2 DAILY Scenario

**Purpose:** Provide balanced daily practice recommendations.

**Algorithm:**
- Combines collaborative filtering and content-based recommendations
- Considers user's current skill level
- Balances difficulty distribution
- Excludes recently solved problems (configurable)

**Best Practices:**
- Use `size=10` for a full practice session
- Use `includeSolved=false` to avoid repetition
- Suitable for regular daily use

### 5.3 SIMILAR Scenario

**Purpose:** Find problems similar to a specific problem.

**Algorithm:**
- Content-based similarity using tags and features
- Considers difficulty proximity
- Requires `sourceProblemId` parameter

**Best Practices:**
- Always provide `sourceProblemId`
- Use after user completes a problem
- Helps reinforce specific concepts

### 5.4 WEAK_POINT Scenario

**Purpose:** Strengthen user's weak areas.

**Algorithm:**
- Analyzes user's submission history
- Identifies tags with low success rate
- Recommends problems targeting weak areas
- Starts with easier problems in weak areas

**Best Practices:**
- Use for targeted improvement
- Combine with `targetTags` for specific focus
- Track progress over time

### 5.5 CHALLENGE Scenario

**Purpose:** Push user limits with harder problems.

**Algorithm:**
- Recommends problems slightly above current level
- Focuses on Hard difficulty problems
- Considers user's potential for growth

**Best Practices:**
- Use for users seeking advancement
- Limit frequency to avoid frustration
- Combine with easy problems for balance

---

## 6. Error Handling

### 6.1 Error Response Format

```json
{
  "success": false,
  "code": 400,
  "message": "Invalid request: userId is required",
  "data": null
}
```

### 6.2 Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| 0 | 200 | Success |
| 400 | 400 | Bad Request - Invalid parameters |
| 404 | 404 | Not Found - User or resource not found |
| 500 | 500 | Internal Server Error |
| -1 | 500 | Generic error |

### 6.3 Common Error Examples

**Missing Required Field:**
```json
{
  "success": false,
  "code": 400,
  "message": "User ID is required",
  "data": null
}
```

**Invalid Scenario:**
```json
{
  "success": false,
  "code": 400,
  "message": "Invalid scenario: UNKNOWN",
  "data": null
}
```

**Service Unavailable:**
```json
{
  "success": false,
  "code": 500,
  "message": "Internal server error: Service unavailable",
  "data": null
}
```

### 6.4 Error Handling Best Practices

1. Always check `success` field before processing data
2. Handle both `code` and `message` for error display
3. Implement retry logic for 500 errors
4. Log error details for debugging

---

## 7. Rate Limiting

### 7.1 Current Limits

| Endpoint | Rate Limit | Window |
|----------|------------|--------|
| POST /api/recommend | 100 requests | 1 minute |
| GET /api/recommend/health | Unlimited | - |

### 7.2 Rate Limit Headers

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1710412800
```

### 7.3 Rate Limit Exceeded Response

```json
{
  "success": false,
  "code": 429,
  "message": "Rate limit exceeded. Please try again later.",
  "data": null
}
```

---

## 8. SDK Examples

### 8.1 Java (Spring RestTemplate)

```java
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.ulticode.recommend.api.dto.*;

public class RecommendClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8080";

    public RecommendResponse<RecommendResult> getRecommendations(String userId, int size) {
        String url = baseUrl + "/api/recommend";

        RecommendRequest request = RecommendRequest.builder()
                .userId(userId)
                .size(size)
                .scenario(RecommendScenario.DAILY)
                .includeSolved(false)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RecommendRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<RecommendResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                RecommendResponse.class
        );

        return response.getBody();
    }
}
```

### 8.2 JavaScript (Fetch API)

```javascript
async function getRecommendations(userId, size = 10) {
  const response = await fetch('http://localhost:8080/api/recommend', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      userId: userId,
      size: size,
      scenario: 'DAILY',
      includeSolved: false
    })
  });

  const data = await response.json();

  if (!data.success) {
    throw new Error(`API Error: ${data.message}`);
  }

  return data.data;
}

// Usage
getRecommendations('user-123', 10)
  .then(result => {
    console.log('Recommendations:', result.items);
  })
  .catch(error => {
    console.error('Error:', error.message);
  });
```

### 8.3 Python (requests)

```python
import requests
from typing import Dict, List, Optional

class RecommendClient:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url

    def get_recommendations(
        self,
        user_id: str,
        size: int = 10,
        scenario: str = "DAILY",
        source_problem_id: Optional[int] = None,
        target_tags: Optional[List[str]] = None,
        include_solved: bool = False
    ) -> Dict:
        url = f"{self.base_url}/api/recommend"

        payload = {
            "userId": user_id,
            "size": size,
            "scenario": scenario,
            "includeSolved": include_solved
        }

        if source_problem_id:
            payload["sourceProblemId"] = source_problem_id

        if target_tags:
            payload["targetTags"] = target_tags

        response = requests.post(url, json=payload)
        response.raise_for_status()

        data = response.json()

        if not data.get("success"):
            raise Exception(f"API Error: {data.get('message')}")

        return data.get("data")

# Usage
client = RecommendClient()

# Daily recommendations
result = client.get_recommendations("user-123", size=10)
for item in result["items"]:
    print(f"{item['title']} - {item['difficulty']} (score: {item['score']})")

# Similar problems
result = client.get_recommendations(
    user_id="user-123",
    size=5,
    scenario="SIMILAR",
    source_problem_id=42
)

# With target tags
result = client.get_recommendations(
    user_id="user-123",
    size=5,
    scenario="DAILY",
    target_tags=["array", "dynamic-programming"]
)
```

### 8.4 cURL Examples

**Basic Request:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","size":10,"scenario":"DAILY"}'
```

**Similar Problems:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","size":5,"scenario":"SIMILAR","sourceProblemId":42}'
```

**With Target Tags:**
```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-123","size":5,"scenario":"DAILY","targetTags":["array","two-pointers"]}'
```

**Health Check:**
```bash
curl -X GET http://localhost:8080/api/recommend/health
```

---

## Appendix A: Data Types

### RecommendScenario Enum

| Value | Display Name | Description |
|-------|--------------|-------------|
| DAILY | Daily Practice | Daily practice recommendations |
| SIMILAR | Similar Problems | Similar problem recommendations |
| WEAK_POINT | Weak Point | Weak point strengthening recommendations |
| CHALLENGE | Challenge Mode | Challenge mode recommendations |

### Difficulty Levels

| Value | Description |
|-------|-------------|
| Easy | Beginner-friendly problems |
| Medium | Intermediate difficulty |
| Hard | Advanced/Expert level |

---

## Appendix B: Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-03-14 | Initial API release |

---

*Document Version: 1.0*
*Last Updated: 2026-03-14*
*Author: UltiCode Development Team*
