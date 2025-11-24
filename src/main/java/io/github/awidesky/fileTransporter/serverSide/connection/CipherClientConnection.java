package io.github.awidesky.fileTransporter.serverSide.connection;

import io.github.awidesky.fileTransporter.serverSide.selectedFile.SelectedFile;
import io.github.awidesky.jCipherUtil.cipher.symmetric.SymmetricCipherUtil;

public class CipherClientConnection extends ClientConnection {

	private final SymmetricCipherUtil cipher;
	public CipherClientConnection(SymmetricCipherUtil cipher) {
		this.cipher = cipher;
	}

	@Override
	protected boolean sendFile(SelectedFile f) {
		return false;
	}

	@Override
	protected void closeConnection() {
		// TODO Auto-generated method stub
		
	}

}
