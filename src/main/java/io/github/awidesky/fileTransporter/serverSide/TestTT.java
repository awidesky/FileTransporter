package io.github.awidesky.fileTransporter.serverSide;

import java.security.PublicKey;
import java.util.Random;

import io.github.awidesky.jCipherUtil.CipherEncryptEngine;
import io.github.awidesky.jCipherUtil.cipher.symmetric.aes.AESKeySize;
import io.github.awidesky.jCipherUtil.cipher.symmetric.aes.AES_GCMCipherUtil;
import io.github.awidesky.jCipherUtil.cipher.symmetric.key.KeyMetadata;
import io.github.awidesky.jCipherUtil.key.keyExchange.EllipticCurveKeyExchanger;
import io.github.awidesky.jCipherUtil.key.keyExchange.ecdh.ECDHCurves;
import io.github.awidesky.jCipherUtil.key.keyExchange.ecdh.ECDHKeyExchanger;

public class TestTT {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		EllipticCurveKeyExchanger k1 = new ECDHKeyExchanger(ECDHCurves.secp256r1); 
		EllipticCurveKeyExchanger k2 = new ECDHKeyExchanger(ECDHCurves.secp256r1); 

		// PublicKey object
		PublicKey p1 = k1.init();
		k2.init();


		// exchange keys with either PublicKet object or byte array
		byte[] key1 = k1.exchangeKey(p1);
		System.out.println("len : " + key1.length);
		
		int s = 32 * 1024;
		Random r = new Random();
		char[] password = "tH!s1Smyp@Ssw0rd".toCharArray();
		CipherEncryptEngine c = new AES_GCMCipherUtil.Builder(AESKeySize.SIZE_256)
		                    .bufferSize(s)
		                    .keyMetadata(KeyMetadata.DEFAULT)
		                    .build(password)
		                    .cipherEncryptEngine();
		
		
		byte[] arr = new byte[s];
		byte[] out;
		for(int i = 0; i < 10; i++) {
			r.nextBytes(arr);
			out = c.update(arr);
			System.out.printf("in : %6d, out : %6d\n", arr.length, out.length);
		}
		
		arr = new byte[s/2];
		r.nextBytes(arr);
		out = c.doFinal(arr);
		System.out.printf("in : %6d, out : %6d\n", arr.length, out.length);
		
	}

}
