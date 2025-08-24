package io.github.awidesky.serverSide;

import java.io.File;

public record SelectedFile (File actual, String relative) {
	@Override
	public String toString() {
		return actual.getName();
	}
}
