package com.browseengine.bobo.api;

import java.io.IOException;

public class BoboBrowser extends MultiBoboBrowser {
	/**
	 * @param reader BoboMultiReader
	 * @throws IOException io.
	 */
	public BoboBrowser(BoboMultiReader reader) throws IOException {
		super(reader);
	}
}
