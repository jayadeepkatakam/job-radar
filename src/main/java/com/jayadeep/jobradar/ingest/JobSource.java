package com.jayadeep.jobradar.ingest;

import java.util.List;

/** A job-board API that lists a company's open postings. */
public interface JobSource {

    /** The value used in {@code jobradar.boards[].source}, e.g. "greenhouse". */
    String name();

    List<RawJob> fetch(String boardToken, String companyName);
}
