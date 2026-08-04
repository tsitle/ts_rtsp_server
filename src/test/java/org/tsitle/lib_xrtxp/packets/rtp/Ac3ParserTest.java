package org.tsitle.lib_xrtxp.packets.rtp;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Parser;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketAc3;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ac3ParserTest {

	@Test
	void parse_ac3() throws Exception {
		BufferExt orgFrame = BufferExt.decodeHexString("""
				0b77ee2e524043e106f3cdc303286065ffda95c735df48ce556dc57b5d53
				f7448cf9e63abd6e60569c65fd28912240147334adc9a93245068d493ecb
				9dc2ca60f5c5cfd07fce149cd9837fa5c843f5e612705dcb81fdf465841d
				471099cc4de549660d6c8ce38b82f6b115b34d13013228bb0e59269896ed
				885126734e0513c8e94e132a32a32a32932a54ccd23726c4e9201c35257b
				6e76e020000000000203d7001b707c9db1b2d89191a4c12bcc5d5fb9411c
				52af5521d55875d5dc38695f3d4ac1f417b4d5d3556d5527d105ba530a75
				6e242bba7efb806a44875561d7cd9114eb7ce91f64309ca6555d33b4b6d0
				c284e9cd6855dee6b709edb5ea2e80daffec85c2e3af6cd503b8ce9b8c33
				75f62ece0a3f073250c8cde89578cdc615cb3712b10382108e544d937182
				7b900763738737d69d0e1dbb886da4b6a896ee007632e173d7b669825b66
				4dc3e6ca9b7cb1460001d1e7d391a1fbfb3b622fab1fc2405dd862e8ec46
				1264cedc68d8bece584692a3bbf9258846bb9e533327477311c72835bdad
				cfd25f34a7046426b4d36dba2debf3e9c8c8fdfb9eb117d391dd37431a71
				563b4c0000f0dc37f9bcbe3860748c2687afae4801632c62ae0241dc887e
				8407241b934f3c47bedbce5196691ea7a6198c9f7a935c6c1b41563266df
				70062c6db6dae6d47e701c7cce5f14323a461743d7cf06d72c1b0b182078
				49b1832e41fbe924000267f4535f015ffe37b1cfbbeecd0c28f2f2ab68c1
				2919abc019367045c9f7823e1fd2484b6cc330e3d56c95603c45cd9169b1
				aebd616d75e9d9274d2da875d560c60669f4139f0163fe37b3cfbbeeced6
				d723dad848c0001e5ae83a760ff4027b2d8776fe0d4af6e7c3456ca464c4
				ac8c03a06d21c4bad219de1ad354ed08a89367521e36176d31b6a9b6639d
				42482c56d9dcfdb1a2081837b61edae84a7c0ff482bb2d47f6fe0ddadcd2
				6b4ad9008b2a0b775f22534043e106f3cdc303086061f55ad91855fbe41b
				7d3db164584cea416daeb4603c3da9c98e5f0df4e7ae64b9a6e1f937ee61
				404309f29ad4ab2daf1e9aa7b510ef083080f0f6a7f9bb82aa3adf9bb14d
				3114aeac264c5c8aa4911cbe84e92d6af09f3daf09d4332fe99428520031
				48bfe203a4d8e8fc653001f17c3e042ed129205eb8897e384a1899fa6ea1
				b96e5386958ae702a95bd351d9ed4ea6248ef37dff01f323e1960dac1480
				04b60773739d6b7631e2e11bb0c524ffc81e9761a2f192c7ffc61b9a16c2
				37680c00000a4be81db5b83106aa1d2c341adf642ffd9d00bbf5c3bd8282
				946f2dfcc9ff4ef3c81918ccda2b4ecc345b4dd4d4a8b16753d9000474b0
				cea099405e8041ab8cf291db072c97a246b6d03935a076d6d0c41aa878b0
				d06bb65071b6b4d4980007b718cd04e1ffabc4f4a06fa85d0d6eea830583
				07785ba130d5905e6baa20971e374d316c3ad9f51b44adb6ba4e27561ad7
				77f1be378f1c18a921bf4301088029972951bd190d398d6e1f0c67331387
				feaf0fca81bea1946d681d948da93000065f44c1929f6a844b9d0c6f1085
				249bb803b73dd8b8bbd8482fdf31c75be0e2a3858922970af3eec19594ae
				1adb6659a50f172a7c326ebc2030543fb1ee75054554b52268361654d8a5
				997d030a4afd6a212e64217c4214daacdb920b926000044567dda17b59a8
				8fa1dfe8e6bf97e4ed50a61e8ff419c4705ea3ac33719023cd25879ec1df
				a10e12ec35101a3a9cbda5222233be96d0e67516c293b10a201f2348d0c6
				1a13ad92dcc392d5af7e82ed26823e677fa39b01b1ac57c551a4c0002797
				ac828f8d991bd2335c70a8f9035a3e6face2e5136c8fc292d2590a9752e7
				accb10a55e5cbac3d6c129dbdb4f2cb6552b302e2bee3d2a1a36d9a9564f
				582b1225596e90236c819924a0de51f24037e4af288d71c1a1e31354e0c8
				390000000000000000000000a803""");

		AudioAc3Parser ac3Parser = new AudioAc3Parser();
		AudioAc3Info ac3InfoOrg = ac3Parser.parseAc3Data(new BufferView(orgFrame));

		assertEquals(696, ac3InfoOrg.frameLength);
		assertEquals(696, ac3InfoOrg.samplesLength);
		assertEquals(0, ac3InfoOrg.samplesOffset);
		assertEquals(AudioAc3Info.Samplerate.SR44100, ac3InfoOrg.samplerate);
		assertEquals(AudioAc3Info.Bitrate.BR160, ac3InfoOrg.bitrate);
		assertEquals(AudioAc3Info.AudioCodingMode.ACM_2ZERO, ac3InfoOrg.audioCodingMode);

		//
		BufferExt secondSyncframe = orgFrame.slice(ac3InfoOrg.frameLength);
		AudioAc3Info secondAc3Info = ac3Parser.parseAc3Data(new BufferView(secondSyncframe));

		assertEquals(secondSyncframe.getUsed(), secondAc3Info.frameLength);
		assertEquals(698, secondAc3Info.frameLength);
		assertEquals(698, secondAc3Info.samplesLength);
		assertEquals(0, secondAc3Info.samplesOffset);
		assertEquals(AudioAc3Info.Samplerate.SR44100, secondAc3Info.samplerate);
		assertEquals(AudioAc3Info.Bitrate.BR160, secondAc3Info.bitrate);
		assertEquals(AudioAc3Info.AudioCodingMode.ACM_2ZERO, secondAc3Info.audioCodingMode);

		//
		RtpPacketAc3 packetOne = new RtpPacketAc3(
				new ParamsContainerBase(
						RtspProtoIdXsrc.of(0x12345678L),
						RtspProtoRtpSeqNr.of(0x1234),
						true,
						RtspProtoRtpTimestamp.of(0xABCDEF01L)
					),
				1,
				true,
				ac3InfoOrg,
				new BufferView(orgFrame, ac3InfoOrg.samplesOffset, ac3InfoOrg.samplesLength)
			);

		RtpPacketAc3 packetTwo = new RtpPacketAc3(packetOne.getPacketBufferPtr());

		assertEquals(packetOne.getPacketBufferPtr(), packetTwo.getPacketBufferPtr());
		assertEquals(packetOne.getHdFrameType(), packetTwo.getHdFrameType());
		assertEquals(packetOne.getHdNumberOfFragments(), packetTwo.getHdNumberOfFragments());
	}

}
