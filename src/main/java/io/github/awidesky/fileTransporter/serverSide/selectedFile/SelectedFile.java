package io.github.awidesky.fileTransporter.serverSide.selectedFile;

import java.io.File;

public record SelectedFile (File actual, String relative) {
	@Override
	public String toString() {
		return relative + " (" + actual.getAbsolutePath() + ")";
	}
	public String fileName() {
		return actual.getName();
	}
}
