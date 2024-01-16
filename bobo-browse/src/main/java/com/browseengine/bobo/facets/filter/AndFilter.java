package com.browseengine.bobo.facets.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;

import com.browseengine.bobo.docidset.AndDocIdSet;

public class AndFilter extends Filter {

  private final List<? extends Filter> _filters;

  public AndFilter(List<? extends Filter> filters) {
    _filters = filters;
  }

  @Override
  public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
    if (_filters.size() == 1) {
      return _filters.get(0).getDocIdSet(context, acceptDocs);
    } else {
      List<DocIdSet> list = new ArrayList<>(_filters.size());
      for (Filter f : _filters) {
        list.add(f.getDocIdSet(context, acceptDocs));
      }
      return new AndDocIdSet(list);
    }
  }
}
