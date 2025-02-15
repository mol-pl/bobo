package com.browseengine.bobo.service;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.lucene.search.DocIdSet;

import com.browseengine.bobo.docidset.AndDocIdSet;
import com.browseengine.bobo.docidset.NotDocIdSet;
import com.browseengine.bobo.docidset.OrDocIdSet;

public class DefaultBrowseQueryParser implements BrowseQueryParser {

  @Override
  public DocIdSet parse(SelectionNode[] selectionNodes, SelectionNode[] notSelectionNodes,
      int maxDoc) {
    DocIdSet docSet = null;
    DocIdSet selSet = null;

    if (selectionNodes != null && selectionNodes.length > 0) {
      ArrayList<DocIdSet> selSetList = new ArrayList<>(selectionNodes.length);
      for (SelectionNode selectionNode : selectionNodes) {
        DocIdSet ds = selectionNode.getDocSet();

        if (ds != null) {
          selSetList.add(ds);
        }
      }

      if (selSetList.size() > 0) {
        if (selSetList.size() == 1) {
          selSet = selSetList.get(0);
        } else {
          selSet = new com.browseengine.bobo.docidset.AndDocIdSet(selSetList);
        }
      }
    }

    DocIdSet notSelSet = null;

    if (notSelectionNodes != null && notSelectionNodes.length > 0) {
      ArrayList<DocIdSet> notSelSetList = new ArrayList<>(notSelectionNodes.length);
      for (SelectionNode selectionNode : notSelectionNodes) {
        DocIdSet ds = selectionNode.getDocSet();

        if (ds != null) {
          notSelSetList.add(ds);
        }

        if (notSelSetList.size() > 0) {
          if (notSelSetList.size() == 1) {
            notSelSet = notSelSetList.get(0);
          } else {
            notSelSet = new OrDocIdSet(notSelSetList);
          }
        }
      }
    }

    if (notSelSet != null) {
      notSelSet = new NotDocIdSet(notSelSet, maxDoc);
    }

    if (selSet != null && notSelSet != null) {
      DocIdSet[] sets = new DocIdSet[] { selSet, notSelSet };
      docSet = new AndDocIdSet(Arrays.asList(sets));
    } else if (selSet != null) {
      docSet = selSet;
    } else if (notSelSet != null) {
      docSet = notSelSet;
    }

    return docSet;
  }
}
