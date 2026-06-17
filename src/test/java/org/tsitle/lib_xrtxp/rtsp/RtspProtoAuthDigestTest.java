package org.tsitle.lib_xrtxp.rtsp;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RtspProtoAuthDigestTest {

	@Test
	public void testAuthDigest() {
		/*
		 * Method: DESCRIBE
		 * Password: ABCDEFGH
		 * WWW-Authenticate: Digest realm="Realm_A1B2C3D4E5F6_G7H8I9_J10K11", nonce="362e04b16faee54441fc9d536a04ed40", algorithm="MD5"
		 * Authorization: Digest username="admin", realm="Realm_A1B2C3D4E5F6_G7H8I9_J10K11", nonce="362e04b16faee54441fc9d536a04ed40", uri="rtsp://localhost:1151/buerrow-av.stream", response="160ca64f99160e6ea50a46e24a197a13"
		 */

		String actual = RtspProtoAuthDigest.computeAuthResponse(
				"admin",
				"ABCDEFGH",
				"rtsp://localhost:1151/buerrow-av.stream",
				RtspProtoMessageType.DESCRIBE,
				"Realm_A1B2C3D4E5F6_G7H8I9_J10K11",
				"362e04b16faee54441fc9d536a04ed40"
			);

		String expected = "160ca64f99160e6ea50a46e24a197a13";

		assertEquals(expected, actual);
	}

}
