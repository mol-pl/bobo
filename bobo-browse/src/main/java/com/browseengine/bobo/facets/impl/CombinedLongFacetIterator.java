package com.browseengine.bobo.facets.impl;

import java.util.List;
import java.util.NoSuchElementException;

import com.browseengine.bobo.api.LongFacetIterator;
import com.browseengine.bobo.facets.data.TermLongList;

public class CombinedLongFacetIterator extends LongFacetIterator {

	public long facet;

	private static class LongIteratorNode {

		public LongFacetIterator _iterator;
		public long _curFacet;
		public int _curFacetCount;

		public LongIteratorNode(LongFacetIterator iterator) {
			_iterator = iterator;
			_curFacet = TermLongList.VALUE_MISSING;
			_curFacetCount = 0;
		}

		public boolean fetch(int minHits) {
			if (minHits > 0) {
				minHits = 1;
			}
			if ((_curFacet = _iterator.nextLong(minHits)) != TermLongList.VALUE_MISSING) {
				_curFacetCount = _iterator.count;
				return true;
			}
			_curFacet = TermLongList.VALUE_MISSING;
			_curFacetCount = 0;
			return false;
		}
	}

	private final LongFacetPriorityQueue _queue;

	private List<LongFacetIterator> _iterators;

	private CombinedLongFacetIterator(final int length) {
		_queue = new LongFacetPriorityQueue();
		_queue.initialize(length);
	}

	public CombinedLongFacetIterator(final List<LongFacetIterator> iterators) {
		this(iterators.size());
		_iterators = iterators;
		for (LongFacetIterator iterator : iterators) {
			LongIteratorNode node = new LongIteratorNode(iterator);
			if (node.fetch(1)) {
				_queue.add(node);
			}
		}
		facet = TermLongList.VALUE_MISSING;
		count = 0;
	}

	public CombinedLongFacetIterator(final List<LongFacetIterator> iterators, int minHits) {
		this(iterators.size());
		_iterators = iterators;
		for (LongFacetIterator iterator : iterators) {
			LongIteratorNode node = new LongIteratorNode(iterator);
			if (node.fetch(minHits)) {
				_queue.add(node);
			}
		}
		facet = TermLongList.VALUE_MISSING;
		count = 0;
	}

	/**
	 * (non-Javadoc)
	 * @return formatted facet.
	 * @see com.browseengine.bobo.api.FacetIterator#facet
	 */
	public String getFacet() {
		if (facet == TermLongList.VALUE_MISSING) {
			return null;
		}
		return format(facet);
	}

	@Override
	public String format(long val) {
		return _iterators.get(0).format(val);
	}

	@Override
	public String format(Object val) {
		return _iterators.get(0).format(val);
	}

	/**
     * (non-Javadoc)
	 * @return count.
     * @see com.browseengine.bobo.api.FacetIterator#count
	 */
	public int getFacetCount() {
		return count;
	}

	/**
     * (non-Javadoc)
     * @see com.browseengine.bobo.api.FacetIterator#next()
	 */
	@Override
	public String next() {
		if (!hasNext()) {
			throw new NoSuchElementException("No more facets in this iteration");
		}

		LongIteratorNode node = _queue.top();

		facet = node._curFacet;
		long next = TermLongList.VALUE_MISSING;
		count = 0;
		while (hasNext()) {
			node = _queue.top();
			next = node._curFacet;
			if ((next != TermLongList.VALUE_MISSING) && (next != facet)) {
				return format(facet);
			}
			count += node._curFacetCount;
			if (node.fetch(1)) {
				_queue.updateTop();
			} else {
				_queue.pop();
			}
		}
		return null;
	}

	/**
	 * This version of the next() method applies the minHits from the facet spec
	 * before returning the facet and its hitcount.
	 *
	 * @param minHits the minHits from the facet spec for CombinedFacetAccessible
	 * @return The next facet that obeys the minHits
	 */
	@Override
	public String next(int minHits) {
		int qsize = _queue.size();
		if (qsize == 0) {
			facet = TermLongList.VALUE_MISSING;
			count = 0;
			return null;
		}

		LongIteratorNode node = _queue.top();
		facet = node._curFacet;
		count = node._curFacetCount;
		while (true) {
			if (node.fetch(minHits)) {
				node = _queue.updateTop();
			} else {
				_queue.pop();
				if (--qsize > 0) {
					node = _queue.top();
				} else {
					// we reached the end. check if this facet obeys the minHits
					if (count < minHits) {
						facet = TermLongList.VALUE_MISSING;
						count = 0;
						return null;
					}
					break;
				}
			}
			long next = node._curFacet;
			if (next != facet) {
				// check if this facet obeys the minHits
				if (count >= minHits) {
					break;
				}
				// else, continue iterating to the next facet
				facet = next;
				count = node._curFacetCount;
			} else {
				count += node._curFacetCount;
			}
		}
		return format(facet);
	}

	/*
   * (non-Javadoc)
   * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		return (_queue.size() > 0);
	}

	/*
   * (non-Javadoc)
   * @see java.util.Iterator#remove()
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove() method not supported for Facet Iterators");
	}

	/**
	 * Lucene PriorityQueue
	 */
	public static class LongFacetPriorityQueue {

		private int size;
		private int maxSize;
		protected LongIteratorNode[] heap;

		/**
		 * Subclass constructors must call this.
		 * @param maxSize .
		 */
		protected final void initialize(int maxSize) {
			size = 0;
			int heapSize;
			if (0 == maxSize) // We allocate 1 extra to avoid if statement in top()
			{
				heapSize = 2;
			} else {
				heapSize = maxSize + 1;
			}
			heap = new LongIteratorNode[heapSize];
			this.maxSize = maxSize;
		}

		public final void put(LongIteratorNode element) {
			size++;
			heap[size] = element;
			upHeap();
		}

		public final LongIteratorNode add(LongIteratorNode element) {
			size++;
			heap[size] = element;
			upHeap();
			return heap[1];
		}

		public boolean insert(LongIteratorNode element) {
			return insertWithOverflow(element) != element;
		}

		public LongIteratorNode insertWithOverflow(LongIteratorNode element) {
			if (size < maxSize) {
				put(element);
				return null;
			} else if (size > 0 && !(element._curFacet < heap[1]._curFacet)) {
				LongIteratorNode ret = heap[1];
				heap[1] = element;
				adjustTop();
				return ret;
			} else {
				return element;
			}
		}

		/**
		 * Returns the least element of the PriorityQueue in constant time.
		 * @return first element.
		 */
		public final LongIteratorNode top() {
			// We don't need to check size here: if maxSize is 0,
			// then heap is length 2 array with both entries null.
			// If size is 0 then heap[1] is already null.
			return heap[1];
		}

		/**
		 * Removes and returns the least element of the PriorityQueue in log(size)
		 * time.
		 * @return last element.
		 */
		public final LongIteratorNode pop() {
			if (size > 0) {
				LongIteratorNode result = heap[1]; // save first value
				heap[1] = heap[size]; // move last to first
				heap[size] = null; // permit GC of objects
				size--;
				downHeap(); // adjust heap
				return result;
			} else {
				return null;
			}
		}

		public final void adjustTop() {
			downHeap();
		}

		public final LongIteratorNode updateTop() {
			downHeap();
			return heap[1];
		}

		/**
		 * Returns the number of elements currently stored in the PriorityQueue.
		 * @return number of elements.
		 */
		public final int size() {
			return size;
		}

		/**
		 * Removes all entries from the PriorityQueue.
		 */
		public final void clear() {
			for (int i = 0; i <= size; i++) {
				heap[i] = null;
			}
			size = 0;
		}

		private final void upHeap() {
			int i = size;
			LongIteratorNode node = heap[i]; // save bottom node
			int j = i >>> 1;
			while (j > 0 && (node._curFacet < heap[j]._curFacet)) {
				heap[i] = heap[j]; // shift parents down
				i = j;
				j = j >>> 1;
			}
			heap[i] = node; // install saved node
		}

		private final void downHeap() {
			int i = 1;
			LongIteratorNode node = heap[i]; // save top node
			int j = i << 1; // find smaller child
			int k = j + 1;
			if (k <= size && (heap[k]._curFacet < heap[j]._curFacet)) {
				j = k;
			}
			while (j <= size && (heap[j]._curFacet < node._curFacet)) {
				heap[i] = heap[j]; // shift up child
				i = j;
				j = i << 1;
				k = j + 1;
				if (k <= size && (heap[k]._curFacet < heap[j]._curFacet)) {
					j = k;
				}
			}
			heap[i] = node; // install saved node
		}
	}

	@Override
	public long nextLong() {
		if (!hasNext()) {
			throw new NoSuchElementException("No more facets in this iteration");
		}

		LongIteratorNode node = _queue.top();

		facet = node._curFacet;
		long next = TermLongList.VALUE_MISSING;
		count = 0;
		while (hasNext()) {
			node = _queue.top();
			next = node._curFacet;
			if ((next != TermLongList.VALUE_MISSING) && (next != facet)) {
				return facet;
			}
			count += node._curFacetCount;
			if (node.fetch(1)) {
				_queue.updateTop();
			} else {
				_queue.pop();
			}
		}
		return TermLongList.VALUE_MISSING;
	}

	@Override
	public long nextLong(int minHits) {
		int qsize = _queue.size();
		if (qsize == 0) {
			facet = TermLongList.VALUE_MISSING;
			count = 0;
			return TermLongList.VALUE_MISSING;
		}

		LongIteratorNode node = _queue.top();
		facet = node._curFacet;
		count = node._curFacetCount;
		while (true) {
			if (node.fetch(minHits)) {
				node = _queue.updateTop();
			} else {
				_queue.pop();
				if (--qsize > 0) {
					node = _queue.top();
				} else {
					// we reached the end. check if this facet obeys the minHits
					if (count < minHits) {
						facet = TermLongList.VALUE_MISSING;
						count = 0;
					}
					break;
				}
			}
			long next = node._curFacet;
			if (next != facet) {
				// check if this facet obeys the minHits
				if (count >= minHits) {
					break;
				}
				// else, continue iterating to the next facet
				facet = next;
				count = node._curFacetCount;
			} else {
				count += node._curFacetCount;
			}
		}
		return facet;
	}
}
