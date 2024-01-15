package com.browseengine.bobo.docidset;

import java.io.IOException;
import java.util.logging.Level;

import org.apache.lucene.search.DocIdSetIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ImmutableDocSet extends DocSet {
  private int size = -1;
  private static final Logger logger = LoggerFactory.getLogger(ImmutableDocSet.class);

  @Override
  public void addDoc(int docid) {
    throw new java.lang.UnsupportedOperationException(
        "Attempt to add document to an immutable data structure");
  }

  @Override
  public int size() throws IOException {
    // Do the size if we haven't done it so far.
    if (size < 0) {
      DocIdSetIterator dcit = this.iterator();
      size = 0;
      try {
        while (dcit.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
          size++;
      } catch (IOException e) {
        logger.error("Error computing size..");
        return -1;
      }
    }
    return size;
  }

}
