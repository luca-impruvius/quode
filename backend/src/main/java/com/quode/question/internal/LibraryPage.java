package com.quode.question.internal;

import java.util.List;

/** One page of the Library list, with the total for "N questions" (ADR-0030). */
record LibraryPage(
    List<LibraryItem> items, int page, int size, long totalElements, boolean hasNext) {}
