package org.tsitle.lib_xrtxp.rtsp.sdp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpRaw;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSdpStructured;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpTransport;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumer for Session Description Protocol (SDP) messages (according to RFC-2327 Section 6).
 */
public final class RtspProtoSdpConsumer implements RtspProtoSdpConsumerInterface {

	private enum ParsingState {
		STATE_COMMON,
		STATE_MEDIA,
		STATE_SWITCH_TO_COMMON
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void parseSdpFromDescribe(
				@NonNull RtspProtoDataCntSdpRaw inputSdp,
				@NonNull RtspProtoDataCntSdpStructured outputSdp
			) throws RtspProtoSdpException {
		parseSdpLines(inputSdp, outputSdp);
	}

	@Override
	public void parseUpdatedSdpFromAnnounce(
				@NonNull RtspProtoDataCntSdpRaw inputSdp,
				@NonNull RtspProtoDataCntSdpStructured outputSdp
			) throws RtspProtoSdpException {
		parseSdpLines(inputSdp, outputSdp);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseSdpLines(
				@NonNull RtspProtoDataCntSdpRaw inputSdp,
				@NonNull RtspProtoDataCntSdpStructured outputSdp
			) throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseSdpLines()";

		/*
		 * Example:
		 *   "v=0"
		 *   "o=- 1781786525327 1 IN IP4 192.168.1.1"
		 *   "s=Just A Session"
		 *   "i=demo.stream"
		 *   "t=0 0"
		 *   "a=tool:TS RTSP Server/1.0"
		 *   "a=type:broadcast"
		 *   "a=control:*"
		 *   "a=range:npt=0-"
		 *   "m=video 0 RTP/SAVP 112"  <-- first sub-stream
		 *   "a=rtpmap:112 H264/90000"
		 *   "a=fmtp:112 packetization-mode=1"
		 *   "a=control:substreamidf528764d_dbbd5deb"
		 *   "a=key-mgmt:mikey AQAFAElq4S...AAAEA"
		 *   "m=audio 0 RTP/SAVP 101"  <-- second sub-stream
		 *   "b=AS:128000"
		 *   "a=rtpmap:101 L16/8000/1"
		 *   "a=control:substreamidf528764d_081eb523"
		 *   "a=key-mgmt:mikey AQAFABdQ828BA...8YBAAAAAEA"
		 */

		if (inputSdp.getContentBase().isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Missing SDP Content Base");
		}

		//
		outputSdp.setContentLang(inputSdp.getContentLang());
		outputSdp.setContentBase(inputSdp.getContentBase());

		//
		ParsingState parsingState = ParsingState.STATE_COMMON;
		for (String sdpLine : inputSdp.getSdpLinesAllRaw()) {
			if (sdpLine.length() < 2) {
				continue;
			}
			if (sdpLine.charAt(1) != '=') {
				throw new RtspProtoSdpException(FNC_NAME + ": Invalid SDP line: '" + sdpLine + "'");
			}
			char tmpEntryTp = Character.toLowerCase(sdpLine.charAt(0));
			String tmpEntryVal = cleanUpSdpLine(sdpLine.substring(2).strip());

			do {
				if (parsingState == ParsingState.STATE_SWITCH_TO_COMMON) {
					parsingState = ParsingState.STATE_COMMON;
				}
				if (parsingState == ParsingState.STATE_COMMON) {
					parsingState = parseSdpEntry_common(parsingState, tmpEntryTp, tmpEntryVal, outputSdp);
				} else if (parsingState == ParsingState.STATE_MEDIA) {
					parsingState = parseSdpEntry_media(parsingState, tmpEntryTp, tmpEntryVal, outputSdp);
				}
			} while (parsingState == ParsingState.STATE_SWITCH_TO_COMMON);
		}

		//
		outputSdp.writeProtect();
	}

	private @NonNull ParsingState parseSdpEntry_common(
				@NonNull ParsingState parsingState,
				char entryTp,
				@NonNull String entryVal,
				@NonNull RtspProtoDataCntSdpStructured outputSdp
			) throws RtspProtoSdpException {
		/*
		 * Example:
		 *   "v=0"
		 *   "o=- 178178611111 178178699999 IN IP4 192.168.1.1"
		 *   "s=Just A Session"
		 *   "i=demo.stream"
		 *   "u=http://www.cs.ucl.ac.uk/staff/M.Handley/sdp.03.ps"
		 *   "e=mjh@isi.edu (Mark Handley)"
		 *   "t=0 0"  or  "t=2873397496 2873404696"
		 *   "a=recvonly"
		 *   "a=tool:TS RTSP Server/1.0"
		 *   "a=type:broadcast"
		 *   "a=control:*"
		 *   "a=range:npt=0-"
		 *   "m=video 0 RTP/SAVP 112"  <-- first sub-stream
		 *   ...
		 *   "m=audio 0 RTP/SAVP 101"  <-- second sub-stream
		 *   ...
		 *   "m=application 32416 udp wb"  <-- something else
		 *   "a=orient:portrait"
		 */
		ParsingState resState = parsingState;
		switch (entryTp) {
			case 'v':  // v: Protocol version
				parseCommonVersion(entryVal, outputSdp);
				break;
			case 'o':  // o: Origin
				parseCommonOrigin(entryVal, outputSdp);
				break;
			case 's':  // s: Session mame
				parseCommonSessionName(entryVal, outputSdp);
				break;
			case 'i':  // i: Session information
				parseCommonSessionInfo(entryVal, outputSdp);
				break;
			case 'u':  // u: URI of description
				parseCommonUri(entryVal, outputSdp);
				break;
			case 'e':  // e: Email address
				parseCommonEmail(entryVal, outputSdp);
				break;
			case 'p':  // p: Phone number
				parseCommonPhone(entryVal, outputSdp);
				break;
			case 'c':  // c: Connection information
				parseCommonConnInfo(entryVal, outputSdp);
				break;
			case 'b':  // b: Bandwidth information
				parseCommonBandwidth(entryVal, outputSdp);
				break;
			case 'z':  // z: Time zone adjustments
				parseCommonTzAdj(entryVal, outputSdp);
				break;
			case 'k':  // k: Encryption key
				// we ignore this entirely since we only support MIKEY and legacy SDES
				break;
			case 'a':  // a: Session attribute
				parseCommonSessionAttr(entryVal, outputSdp);
				break;
			case 't':  // t: Time Active
				parseCommonTimeActive(entryVal, outputSdp);
				break;
			case 'r':  // r: Repeat times
				parseCommonRepeatTimes(entryVal, outputSdp);
				break;
			case 'm':  // m: Media description
				parseMediaEntryHeader(entryVal, outputSdp);
				resState = ParsingState.STATE_MEDIA;
				break;
		}
		return resState;
	}

	private @NonNull ParsingState parseSdpEntry_media(
				@NonNull ParsingState parsingState,
				char entryTp,
				@NonNull String entryVal,
				@NonNull RtspProtoDataCntSdpStructured outputSdp
			) throws RtspProtoSdpException {
		/*
		 * Example:
		 *   "a=rtpmap:112 H264/90000"
		 *   "a=fmtp:112 packetization-mode=1"
		 *   "a=control:substreamidf528764d_dbbd5deb"
		 *   "a=key-mgmt:mikey AQAFAElq4S...AAAEA"
		 */
		ParsingState resState = parsingState;
		switch (entryTp) {
			case 'm':  // m: Media description
				resState = ParsingState.STATE_SWITCH_TO_COMMON;
				break;
			case 'i':  // i: Media title
				parseMediaEntryTitle(entryVal, outputSdp);
				break;
			case 'c':  // c: Connection information
				parseMediaEntryConnInfo(entryVal, outputSdp);
				break;
			case 'b':  // b: Bandwidth information
				parseMediaEntryBandwidth(entryVal, outputSdp);
				break;
			case 'k':  // k: Encryption key
				// we ignore this entirely since we only support MIKEY and legacy SDES
				break;
			case 'a':  // a: Media attribute
				parseMediaEntryAttr(entryVal, outputSdp);
				break;
		}
		return resState;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseCommonVersion(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonVersion()";

		/*
		 * The "v=" field gives the version of the Session Description Protocol. There is no minor version number.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonVersion(entryVal);
	}

	private void parseCommonOrigin(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonOrigin()";

		/*
		 * o=<username> <session id> <version> <network type> <address type> <address>
		 *
		 * Username can be "-" or an actual username.
		 * Session ID is recommended to be an NTP timestamp.
		 * Version is the version number for this announcement. It is also recommended to be an NTP timestamp.
		 * Network type can be IN (Internet) or maybe L (Local).
		 * Address type can be IP4 or IP6.
		 * Address can be an IP address or hostname.
		 */

		String[] tmpSplit = entryVal.split(" ");
		if (tmpSplit.length != 6) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		RtspProtoSdpDataConnectionOrigin connInfo = new RtspProtoSdpDataConnectionOrigin(
				tmpSplit[3],
				tmpSplit[4],
				tmpSplit[5]
			);
		RtspProtoSdpDataCommonOrigin commonOrigin = new RtspProtoSdpDataCommonOrigin(
				tmpSplit[0],
				tmpSplit[1],
				tmpSplit[2],
				connInfo
			);
		outputSdp.setCommonOrigin(commonOrigin);
	}

	private void parseCommonSessionName(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonSessionName()";

		/*
		 * The "s=" field is the session name. There must be one and only one "s=" field per session description,
		 * and it must contain ISO 10646 characters
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonSessionName(entryVal);
	}

	private void parseCommonSessionInfo(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonSessionInfo()";

		/*
		 * The "i=" field is information about the session. There may be at most
		 * one session-level "i=" field per session description, and at most one "i=" field per media.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonSessionInfo(entryVal);
	}

	private void parseCommonUri(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonUri()";

		/*
		 * The URI should be a pointer to additional information about the conference.
		 * This field is optional, but if it is present, it should be specified before the first media field.
		 * No more than one URI field is allowed per session description.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonUriDescr(entryVal);
	}

	private void parseCommonEmail(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonEmail()";

		/*
		 * Email and Phone:
		 *
		 * These specify contact information for the person responsible for the conference.
		 * This is not necessarily the same person that created the conference announcement.
		 *
		 * - Either an email field or a phone field must be specified. Additional email and phone fields are allowed.
		 * - If these are present, they should be specified before the first media field.
		 * - More than one email or phone field can be given for a session description.
		 * - Phone numbers should be given in the conventional international format - preceded by a "+ and the
		 *   international country code.
		 *   There must be a space or a hyphen ("-") between the country code and the rest of the phone number.
		 *   Spaces and hyphens may be used to split up a phone field to aid readability if desired.
		 *   For example:
		 *     p=+44-171-380-7777
		 *     or
		 *     p=+1 617 253 6011
		 * - Both email addresses and phone numbers can have an optional free text string associated with them,
		 *   normally giving the name of the person who may be contacted. This should be enclosed in parenthesis
		 *   if it is present.
		 *   For example:
		 *     e=mjh@isi.edu (Mark Handley)
		 *   The alternative RFC822 name quoting convention is also allowed for both email addresses and phone numbers.
		 *   For example:
		 *     e=Mark Handley <mjh@isi.edu>
		 *   The free text string should be in the ISO-10646 character set with UTF-8 encoding,
		 *   or alternatively in ISO-8859-1 or other encodings if the appropriate charset session-level attribute is set.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonEmail(entryVal);
	}

	private void parseCommonPhone(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonPhone()";

		/*
		 * See Email above for more information.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonPhone(entryVal);
	}

	private void parseCommonConnInfo(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonConnInfo()";

		/*
		 * c=<network type> <address type> <connection address>
		 *
		 * Conferences using an IP multicast connection address must also have a time to live (TTL) value present
		 * in addition to the multicast address. TTL values must be in the range 0-255.
		 *
		 * Hierarchical or layered encoding schemes are data streams where the encoding from a single media source
		 * is split into a number of layers. The receiver can choose the desired quality (and hence bandwidth)
		 * by only subscribing to a subset of these layers. Such layered encodings are normally transmitted in
		 * multiple multicast groups to allow multicast pruning. This technique keeps unwanted traffic from sites
		 * only requiring certain levels of the hierarchy.
		 * For applications requiring multiple multicast groups, we allow the following notation to be used for
		 * the connection address:
		 *   <base multicast address>/<ttl>/<number of addresses>
		 * If the number of addresses is not given, it is assumed to be one.
		 * Multicast addresses so assigned are contiguously allocated above the base address, so that, for example:
		 *   c=IN IP4 224.2.1.1/127/3
		 * would state that addresses 224.2.1.1, 224.2.1.2 and 224.2.1.3 are to be used at a ttl of 127.
		 * This is semantically identical to including multiple "c=" lines in a media description:
		 *   c=IN IP4 224.2.1.1/127
		 *   c=IN IP4 224.2.1.2/127
		 *   c=IN IP4 224.2.1.3/127
		 *
		 * Multiple addresses or "c=" lines can only be specified on a per-media basis,
		 * and not for a session-level "c=" field.
		 *
		 * It is illegal for the slash notation described above to be used for IP unicast addresses.
		 */

		String[] tmpSplit = entryVal.split(" ");
		if (tmpSplit.length != 3) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		RtspProtoSdpDataConnectionSession connInfo = new RtspProtoSdpDataConnectionSession(
				tmpSplit[0],
				tmpSplit[1],
				tmpSplit[2]
			);
		outputSdp.setCommonConnInfo(connInfo);
	}

	private void parseCommonBandwidth(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonBandwidth()";

		/*
		 * b=<modifier>:<bandwidth-value>
		 *
		 * This specifies the proposed bandwidth to be used by the session or media, and is optional.
		 *
		 * <bandwidth-value> is in kilobits per second.
		 * <modifier> is a single alphanumeric word giving the meaning of the bandwidth figure.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonBandwidth(entryVal);
	}

	private void parseCommonTzAdj(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonTzAdj()";

		/*
		 * z=<adjustment time> <offset> <adjustment time> <offset> ....
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.setCommonTzAdj(entryVal);
	}

	private void parseCommonSessionAttr(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonSessionAttrs()";

		/*
		 * a=<attribute>
		 * a=<attribute>:<value>
		 *
		 * Attributes are the primary means for extending SDP. Attributes may be defined to be used
		 * as "session-level" attributes, "media-level" attributes, or both.
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.addCommonSessionAttrs(entryVal);
	}

	private void parseCommonTimeActive(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonTimeActive()";

		/*
		 * t=<start time> <stop time>
		 *
		 * Example:
		 *   "t=3034423619 3042462419"
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.addCommonTimeActive(entryVal);
	}

	private void parseCommonRepeatTimes(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseCommonRepeatTimes()";

		/*
		 * r=<repeat interval> <active duration> <list of offsets from start-time>
		 *
		 * Examples:
		 *   "r=604800 3600 0 90000"
		 *   or
		 *   "r=7d 1h 0 25h"
		 */

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.addCommonRepeatTimes(entryVal);
	}

	private void parseMediaEntryHeader(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMediaEntryHeader()";

		/*
		 * m=<media> <port> <transport> <fmt list>
		 * m=<media> <port>/<number of ports> <transport> <fmt list>
		 *
		 * Example with multiple formats:
		 *   "m=audio 49230 RTP/AVP 96 97 98"
		 *   Together with
		 *     "a=rtpmap:96 L8/8000"
		 *     "a=rtpmap:97 L16/8000"
		 *     "a=rtpmap:98 L16/11025/2"
		 * Example for non-RTP:
		 *   "m=application 32416 udp wb"
		 */

		String[] tmpSplit = entryVal.strip().split(" ");
		if (tmpSplit.length < 4) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		RtspProtoSdpMediaType mediaType = RtspProtoSdpMediaType.UNKNOWN;
		RtspProtoSocketPortNr portNr = RtspProtoSocketPortNr.ofEmpty();
		RtspProtoSdpTransport transport = RtspProtoSdpTransport.UNKNOWN;
		int portCount = 1;
		List<String> formats = new ArrayList<>();
		for (int ix = 0; ix < tmpSplit.length; ix++) {
			String tmpSub = tmpSplit[ix].strip();
			switch (ix) {
				case 0:  // mediaType
					mediaType = RtspProtoSdpMediaType.of(tmpSub);
					if (mediaType == RtspProtoSdpMediaType.UNKNOWN) {
						throw new RtspProtoSdpException(FNC_NAME + ": Invalid Media Type: '" + tmpSub + "'");
					}
					break;
				case 1:  // portNr + number of ports
					long tmpPortNrLong;
					if (tmpSub.contains("/")) {
						String[] tmpSubSplit = tmpSub.split("/");
						tmpPortNrLong = parseUlong("PortNr", tmpSubSplit[0]);
						portCount = Long.valueOf(parseUlong("PortCount", tmpSubSplit[1])).intValue();
						try {
							// validate port count
							RtspProtoSocketPortNr.of(portCount);
						} catch (RtspProtoNumberRangeException e) {
							throw new RtspProtoSdpException(FNC_NAME + ": Invalid Port Count: " + e.getMessage());
						}
					} else {
						tmpPortNrLong = parseUlong("PortNr", tmpSub);
					}
					if (tmpPortNrLong != 0L) {
						try {
							portNr = RtspProtoSocketPortNr.of(Long.valueOf(tmpPortNrLong).intValue());
						} catch (RtspProtoNumberRangeException e) {
							throw new RtspProtoSdpException(FNC_NAME + ": Invalid Port Nr.: " + e.getMessage());
						}
					}
					break;
				case 2:  // transport
					transport = RtspProtoSdpTransport.of(tmpSub);
					if (transport == RtspProtoSdpTransport.UNKNOWN) {
						throw new RtspProtoSdpException(FNC_NAME + ": Invalid Transport: '" + tmpSub + "'");
					}
					break;
				default:  // format
					if ((mediaType == RtspProtoSdpMediaType.AUDIO || mediaType == RtspProtoSdpMediaType.VIDEO) &&
							(transport == RtspProtoSdpTransport.RTP_AVP || transport == RtspProtoSdpTransport.RTP_SAVP)) {
						long tmpFmtLong = parseUlong("Format", tmpSub);
						if (tmpFmtLong < 0L || tmpFmtLong > 255L) {
							throw new RtspProtoSdpException(FNC_NAME + ": Invalid Format (is=" + tmpFmtLong + ", min=0, max=255)");
						}
						formats.add(Long.toUnsignedString(tmpFmtLong));
					} else {
						formats.add(tmpSub);
					}
					break;
			}
		}
		//
		RtspProtoSdpDataMediaEntryHeader meh = new RtspProtoSdpDataMediaEntryHeader(
				mediaType,
				portNr,
				portCount,
				transport,
				formats
			);
		outputSdp.addMediaEntry(new RtspProtoSdpDataMediaEntry(meh));
	}

	private void parseMediaEntryTitle(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMediaEntryTitle()";

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.updateLastMediaEntryTitle(entryVal);
	}

	private void parseMediaEntryConnInfo(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMediaEntryConnInfo()";

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		String[] tmpSplit = entryVal.split(" ");
		if (tmpSplit.length != 3) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		RtspProtoSdpDataConnectionMedia connInfo = new RtspProtoSdpDataConnectionMedia(
				tmpSplit[0],
				tmpSplit[1],
				tmpSplit[2]
			);
		outputSdp.updateLastMediaEntryConnInfo(connInfo);
	}

	private void parseMediaEntryBandwidth(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMediaEntryBandwidth()";

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.updateLastMediaEntryBandwidth(entryVal);
	}

	private void parseMediaEntryAttr(@NonNull String entryVal, @NonNull RtspProtoDataCntSdpStructured outputSdp)
			throws RtspProtoSdpException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMediaEntryAttr()";

		if (entryVal.isBlank()) {
			throw new RtspProtoSdpException(FNC_NAME + ": Invalid value: '" + entryVal + "'");
		}
		outputSdp.updateLastMediaEntryAttr(entryVal);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static long parseUlong(@NonNull String desc, @NonNull String str) throws RtspProtoSdpException {
		try {
			return Long.parseUnsignedLong(str.strip());
		} catch (NumberFormatException e) {
			throw new RtspProtoSdpException("Invalid value for " + desc + ": '" + str + "'");
		}
	}

	private static @NonNull String cleanUpSdpLine(@NonNull String input) {
		return input.replaceAll("\\r\\n", "<CRLF>")
					.replaceAll("\\r", "<CR>")
					.replaceAll("\\n", "<LF>")
					.replaceAll("\\t", "<TAB>")
					.replace("'", "\\'");
	}

}
