package com.devops.dvmp;

import java.util.List;

/** First rows of a CSV/TSV file plus inferred column types (Kaggle-style data preview). */
public record FilePreview(List<String> columns, List<String> types, List<List<String>> rows, long totalRows) { }
