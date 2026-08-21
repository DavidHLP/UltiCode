package com.ulticode.common.dto;

/** Typed mapper row for a dashboard bucket and its aggregate count. */
public class DashboardBucketCount {

    private String bucket;
    private Long count;

    public DashboardBucketCount() {
    }

    public DashboardBucketCount(String bucket, Long count) {
        this.bucket = bucket;
        this.count = count;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
