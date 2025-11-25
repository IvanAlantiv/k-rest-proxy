package com.example.krestproxy.dto;

import java.util.List;

public record PaginatedResponse<T>(List<T> data, String nextCursor, boolean hasMore) {
}
