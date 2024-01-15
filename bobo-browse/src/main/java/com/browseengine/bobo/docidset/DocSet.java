package com.browseengine.bobo.docidset;

import java.io.IOException;

import org.apache.lucene.search.DocIdSet;

/**
 * Represents a sorted integer set
 */
public abstract class DocSet extends DocIdSet {

	/**
	 * Add a doc id to the set
	 *
	 * @param docid .
	 * @throws IOException io
	 */
	public abstract void addDoc(int docid) throws IOException;

	/**
	 * Add an array of sorted docIds to the set
	 *
	 * @param docids .
	 * @param start  .
	 * @param len    .
	 * @throws IOException io
	 */
	public void addDocs(int[] docids, int start, int len) throws IOException {
		int i = start;
		while (i < len) {
			addDoc(docids[i++]);
		}
	}

	/**
	 * Return the set size
	 *
	 * @param target .
	 * @return true if present, false otherwise
	 * @throws IOException io
	 */
	public boolean find(int target) throws IOException {
		return findWithIndex(target) > -1;
	}

	/**
	 * Return the set size
	 *
	 * @param target .
	 * @return index if present, -1 otherwise
	 * @throws IOException io
	 */
	public int findWithIndex(int target) throws IOException {
		return -1;
	}

	/**
	 * Gets the number of ids in the set
	 *
	 * @return size of the docset
	 * @throws IOException io
	 */
	public int size() throws IOException {
		return 0;
	}

	/**
	 * Return the set size in bytes
	 *
	 * @return index if present, -1 otherwise
	 * @throws IOException io
	 */
	public long sizeInBytes() throws IOException {
		return 0;
	}

	/**
	 * Optimize by trimming underlying data structures
	 * @throws IOException io
	 */
	public void optimize() throws IOException {
	}

}
