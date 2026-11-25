package com.opencampus.api;

public interface CampusService {

    ServiceMetadata getMetadata();

    QueryResult query(QueryRequest request);
}
