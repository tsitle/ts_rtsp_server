package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.MikeyParser;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKdr;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class RtspProtoDataCntSdpStructured {

	private boolean isWriteProtected = false;

	/** Content language(s) (e.g. 'de') */
	private @Nullable String contentLang = null;

	/** Content base (e.g. 'rtsp://some.com/camera.stream/') */
	private @Nullable String contentBase = null;

	private @Nullable String commonVersion = null;
	private @Nullable RtspProtoSdpDataCommonOrigin commonOrigin = null;
	private @Nullable String commonSessionName = null;
	private @Nullable String commonSessionInfo = null;
	private @Nullable String commonUriDescr = null;
	private @Nullable String commonEmail = null;
	private @Nullable String commonPhone = null;
	private @Nullable RtspProtoSdpDataConnectionSession commonConnInfo = null;
	private @Nullable String commonBandwidth = null;
	private @Nullable String commonTzAdj = null;
	private final @NonNull List<@NonNull String> commonSessionAttrs = new ArrayList<>();
	private final @NonNull List<@NonNull String> commonTimeActive = new ArrayList<>();
	private final @NonNull List<@NonNull String> commonRepeatTimes = new ArrayList<>();
	private final @NonNull List<@NonNull RtspProtoSdpDataMediaEntry> mediaEntries = new ArrayList<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getContentLang() {
		return Optional.ofNullable(contentLang);
	}
	public void setContentLang(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.contentLang = value;
	}

	public Optional<String> getContentBase() {
		return Optional.ofNullable(contentBase);
	}
	public void setContentBase(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.contentBase = value;
	}

	public Optional<String> getCommonVersion() {
		return Optional.ofNullable(commonVersion);
	}
	public void setCommonVersion(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonVersion = value;
	}

	public Optional<RtspProtoSdpDataCommonOrigin> getCommonOrigin() {
		return Optional.ofNullable(commonOrigin);
	}
	public void setCommonOrigin(@NonNull RtspProtoSdpDataCommonOrigin value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonOrigin = new RtspProtoSdpDataCommonOrigin(value);
	}

	public Optional<String> getCommonSessionName() {
		return Optional.ofNullable(commonSessionName);
	}
	public void setCommonSessionName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonSessionName = value;
	}

	public Optional<String> getCommonSessionInfo() {
		return Optional.ofNullable(commonSessionInfo);
	}
	public void setCommonSessionInfo(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonSessionInfo = value;
	}

	public Optional<String> getCommonUriDescr() {
		return Optional.ofNullable(commonUriDescr);
	}
	public void setCommonUriDescr(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonUriDescr = value;
	}

	public Optional<String> getCommonEmail() {
		return Optional.ofNullable(commonEmail);
	}
	public void setCommonEmail(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonEmail = value;
	}

	public Optional<String> getCommonPhone() {
		return Optional.ofNullable(commonPhone);
	}
	public void setCommonPhone(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonPhone = value;
	}

	public Optional<RtspProtoSdpDataConnectionSession> getCommonConnInfo() {
		return Optional.ofNullable(commonConnInfo);
	}
	public void setCommonConnInfo(@NonNull RtspProtoSdpDataConnectionSession value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonConnInfo = new RtspProtoSdpDataConnectionSession(value);
	}

	public Optional<String> getCommonBandwidth() {
		return Optional.ofNullable(commonBandwidth);
	}
	public void setCommonBandwidth(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonBandwidth = value;
	}

	public Optional<String> getCommonTzAdj() {
		return Optional.ofNullable(commonTzAdj);
	}
	public void setCommonTzAdj(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonTzAdj = value;
	}

	public @NonNull List<@NonNull String> getCommonSessionAttrs() {
		return new ArrayList<>(commonSessionAttrs);
	}
	public void addCommonSessionAttrs(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonSessionAttrs.add(value);
	}

	public @NonNull List<@NonNull String> getCommonTimeActive() {
		return new ArrayList<>(commonTimeActive);
	}
	public void addCommonTimeActive(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonTimeActive.add(value);
	}

	public @NonNull List<@NonNull String> getCommonRepeatTimes() {
		return new ArrayList<>(commonRepeatTimes);
	}
	public void addCommonRepeatTimes(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.commonRepeatTimes.add(value);
	}

	public @NonNull List<@NonNull RtspProtoSdpDataMediaEntry> getMediaEntries() {
		return new ArrayList<>(mediaEntries);
	}
	public void addMediaEntry(@NonNull RtspProtoSdpDataMediaEntry value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.mediaEntries.add(value);
	}
	public void updateLastMediaEntryTitle(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (mediaEntries.isEmpty()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": No media entries available");
		}
		RtspProtoSdpDataMediaEntry lastEntry = mediaEntries.getLast();
		mediaEntries.set(mediaEntries.size() - 1, new RtspProtoSdpDataMediaEntry(
				new RtspProtoSdpDataMediaEntryHeader(lastEntry.header()),
				value.strip(),
				new RtspProtoSdpDataConnectionMedia(lastEntry.connectionInfo()),
				lastEntry.bandwidth(),
				new ArrayList<>(lastEntry.attributes())
			));
	}
	public void updateLastMediaEntryConnInfo(@NonNull RtspProtoSdpDataConnectionMedia value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (mediaEntries.isEmpty()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": No media entries available");
		}
		RtspProtoSdpDataMediaEntry lastEntry = mediaEntries.getLast();
		mediaEntries.set(mediaEntries.size() - 1, new RtspProtoSdpDataMediaEntry(
				new RtspProtoSdpDataMediaEntryHeader(lastEntry.header()),
				lastEntry.title(),
				new RtspProtoSdpDataConnectionMedia(value),
				lastEntry.bandwidth(),
				new ArrayList<>(lastEntry.attributes())
			));
	}
	public void updateLastMediaEntryBandwidth(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (mediaEntries.isEmpty()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": No media entries available");
		}
		RtspProtoSdpDataMediaEntry lastEntry = mediaEntries.getLast();
		mediaEntries.set(mediaEntries.size() - 1, new RtspProtoSdpDataMediaEntry(
				new RtspProtoSdpDataMediaEntryHeader(lastEntry.header()),
				lastEntry.title(),
				new RtspProtoSdpDataConnectionMedia(lastEntry.connectionInfo()),
				value.strip(),
				new ArrayList<>(lastEntry.attributes())
			));
	}
	public void updateLastMediaEntryAttr(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (mediaEntries.isEmpty()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": No media entries available");
		}
		RtspProtoSdpDataMediaEntry lastEntry = mediaEntries.getLast();
		lastEntry.attributes().add(value.strip());
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Find the first media entry of a given type.
	 * @param mediaType Media type to search for
	 * @return Media entry
	 */
	public Optional<RtspProtoSdpDataMediaEntry> findFirstMediaEntryOfType(@NonNull RtspProtoSdpMediaType mediaType) {
		if (mediaType == RtspProtoSdpMediaType.UNKNOWN) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": Media Type is not set");
		}
		return mediaEntries.stream()
				.filter(entry -> entry.header().mediaType() == mediaType)
				.findFirst();
	}

	/**
	 * Find the media entry for a given Control ID (aka Sub-Stream ID).
	 * @param controlId Control ID to search for
	 * @return Media entry
	 */
	public Optional<RtspProtoSdpDataMediaEntry> findMediaEntryForControlId(@NonNull String controlId) {
		if (controlId.isBlank()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": Control ID must not be blank");
		}
		return mediaEntries.stream()
				.filter(entry -> extractMediaEntryControlId(entry).orElse("").equals(controlId))
				.findFirst();
	}

	/**
	 * Extract the Control ID (aka Sub-Stream ID) from a media entry.
	 * @param entry Media entry to extract the Control ID from.
	 * @return Control ID
	 */
	public Optional<String> extractMediaEntryControlId(@NonNull RtspProtoSdpDataMediaEntry entry) {
		for (String tmpAttr : entry.attributes()) {
			if (tmpAttr.toLowerCase().startsWith("control:")) {
				return Optional.of(tmpAttr.substring("control:".length()).strip());
			}
		}
		return Optional.empty();
	}

	/**
	 * Extract the SRTxP KMD from a media entry.
	 * @param entry Media entry to extract the SRTxP KMD from.
	 * @param idSsrc SSRC of the corresponding Sub-Stream. This will be stored in the returned SRTxP KMD and is crucial for en-/decryption.
	 * @return SRTxP KMD
	 * @throws SrtxpSecurityException If an error occurs during KMD extraction.
	 */
	public Optional<SrtxpKmd> extractMediaEntrySrtxpKmd(@NonNull RtspProtoSdpDataMediaEntry entry, @NonNull RtspProtoIdXsrc idSsrc)
			throws SrtxpSecurityException {
		if (idSsrc.isEmpty()) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ": SSRC must be set");
		}
		String tmpMikeyB64 = null;
		String tmpSdesB64WithTag = null;
		for (String tmpAttr : entry.attributes()) {
			if (tmpAttr.toLowerCase().startsWith("key-mgmt:mikey ")) {
				tmpMikeyB64 = tmpAttr.substring("key-mgmt:mikey ".length());
				break;
			}
			if (tmpAttr.toLowerCase().startsWith("crypto:")) {
				tmpSdesB64WithTag = tmpAttr.substring("crypto:".length());
				break;
			}
		}
		if (tmpMikeyB64 == null && tmpSdesB64WithTag == null) {
			return Optional.empty();
		}

		if (tmpMikeyB64 != null) {
			/*
			 * Modern MIKEY
			 *
			 * "a=key-mgmt:mikey AQAFAE...AAAEA"
			 */
			SrtxpKmd resObj = MikeyParser.parseMickeyMsgIntoKmd(tmpMikeyB64.strip());
			if (! resObj.ssrcId().equals(idSsrc)) {
				throw new SrtxpSecurityException("SSRC mismatch (is=" +
						resObj.ssrcId().toHexString(true) + ", exp=" + idSsrc.toHexString(true) + ")");
			}
			return Optional.of(resObj);
		}

		/*
		 * Legacy SDES:
		 *
		 * "a=crypto:<TAG> AES_CM_128_HMAC_SHA1_80 inline:<MasterKey and MasterSalt> [<session-params>]"
		 * "a=crypto:<TAG> AES_CM_128_HMAC_SHA1_80 inline:<MasterKey and MasterSalt>|<Key Lifetime> [<session-params>]"
		 * "a=crypto:<TAG> AES_CM_128_HMAC_SHA1_80 inline:<MasterKey and MasterSalt>|<Key Lifetime>|<MKI_value>:<MKI_length_bytes> [<session-params>]"
		 * "a=crypto:<TAG> AES_CM_128_HMAC_SHA1_80 inline:<MasterKey and MasterSalt>|<MKI_value>:<MKI_length_bytes> [<session-params>]"
		 */
		return parseSdes(idSsrc, tmpSdesB64WithTag);
	}

	/**
	 * Extract the RTP mapping info for the given format from the given media entry.
	 * @param entry Media entry to extract from
	 * @param fmt Format to search for
	 * @return RTP mapping info (codec, samplerate, channels)
	 */
	public Optional<String> extractMediaEntryRtpMapForFormat(@NonNull RtspProtoSdpDataMediaEntry entry, @NonNull String fmt) {
		if (fmt.isBlank()) {
			throw new IllegalArgumentException("Format must not be blank");
		}
		for (String tmpAttr : entry.attributes()) {
			if (! tmpAttr.toLowerCase().startsWith("rtpmap:")) {
				continue;
			}
			tmpAttr = tmpAttr.substring("rtpmap:".length()).strip();
			String[] tmpAttrParts = tmpAttr.split(" ");
			if (tmpAttrParts.length < 2) {
				continue;
			}
			if (! tmpAttrParts[0].equalsIgnoreCase(fmt)) {
				continue;
			}
			return Optional.of(tmpAttrParts[1]);
		}
		return Optional.empty();
	}

	/**
	 * Extract the Format-specific Parameters for the given format from the given media entry.
	 * @param entry Media entry to extract from
	 * @param fmt Format to search for
	 * @return Format-specific Parameters (e.g. 'packetization-mode=1')
	 */
	public Optional<String> extractMediaEntryFormatSpecificParamsForFormat(@NonNull RtspProtoSdpDataMediaEntry entry, @NonNull String fmt) {
		if (fmt.isBlank()) {
			throw new IllegalArgumentException("Format must not be blank");
		}
		for (String tmpAttr : entry.attributes()) {
			if (! tmpAttr.toLowerCase().startsWith("fmtp:")) {
				continue;
			}
			tmpAttr = tmpAttr.substring("fmtp:".length()).strip();
			String[] tmpAttrParts = tmpAttr.split(" ");
			if (tmpAttrParts.length < 2) {
				continue;
			}
			if (! tmpAttrParts[0].equalsIgnoreCase(fmt)) {
				continue;
			}
			List<String> tmpNewArr = new ArrayList<>(Arrays.asList(tmpAttrParts).subList(1, tmpAttrParts.length));
			return Optional.of(String.join(" ", tmpNewArr));
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		contentLang = null;
		contentBase = null;

		commonVersion = null;
		commonOrigin = null;
		commonSessionName = null;
		commonSessionInfo = null;
		commonUriDescr = null;
		commonEmail = null;
		commonPhone = null;
		commonConnInfo = null;
		commonBandwidth = null;
		commonTzAdj = null;
		commonSessionAttrs.clear();
		commonTimeActive.clear();
		commonRepeatTimes.clear();
		mediaEntries.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntSdpStructured other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		contentLang = other.contentLang;
		contentBase = other.contentBase;

		commonVersion = other.commonVersion;
		commonOrigin = (other.commonOrigin == null ? null : new RtspProtoSdpDataCommonOrigin(other.commonOrigin));
		commonSessionName = other.commonSessionName;
		commonSessionInfo = other.commonSessionInfo;
		commonUriDescr = other.commonUriDescr;
		commonEmail = other.commonEmail;
		commonPhone = other.commonPhone;
		commonConnInfo = (other.commonConnInfo == null ? null : new RtspProtoSdpDataConnectionSession(other.commonConnInfo));
		commonBandwidth = other.commonBandwidth;
		commonTzAdj = other.commonTzAdj;
		commonSessionAttrs.clear();
		commonSessionAttrs.addAll(other.commonSessionAttrs);
		commonTimeActive.clear();
		commonTimeActive.addAll(other.commonTimeActive);
		commonRepeatTimes.clear();
		commonRepeatTimes.addAll(other.commonRepeatTimes);
		mediaEntries.clear();
		mediaEntries.addAll(other.mediaEntries);
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"contentLang=" + (contentLang == null ? "unset" : "'" + contentLang + "'") +
				", contentBase=" + (contentBase == null ? "unset" : "'" + contentBase + "'") +
				(commonVersion != null ? ", commonVersion='" + commonVersion + "'" : "") +
				(commonOrigin != null ? ", commonOrigin=" + commonOrigin : "") +
				(commonSessionName != null ? ", commonSessionName='" + commonSessionName + "'" : "") +
				(commonSessionInfo != null ? ", commonSessionInfo='" + commonSessionInfo + "'" : "") +
				(commonUriDescr != null ? ", commonUriDescr='" + commonUriDescr + "'" : "") +
				(commonEmail != null ? ", commonEmail='" + commonEmail + "'" : "") +
				(commonPhone != null ? ", commonPhone='" + commonPhone + "'" : "") +
				(commonConnInfo != null ? ", commonConnInfo=" + commonConnInfo : "") +
				(commonBandwidth != null ? ", commonBandwidth='" + commonBandwidth + "'" : "") +
				(commonTzAdj != null ? ", commonTzAdj='" + commonTzAdj + "'" : "") +
				(commonSessionAttrs.isEmpty() ? "" : ", commonSessionAttrs=" + listOfStringsToString(commonSessionAttrs)) +
				(commonTimeActive.isEmpty() ? "" : ", commonTimeActive=" + listOfStringsToString(commonTimeActive)) +
				(commonRepeatTimes.isEmpty() ? "" : ", commonRepeatTimes=" + listOfStringsToString(commonRepeatTimes)) +
				(mediaEntries.isEmpty() ? "" : ", mediaEntries=" + listOfMediaToString(mediaEntries)) +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private Optional<SrtxpKmd> parseSdes(@NonNull RtspProtoIdXsrc idSsrc, @NonNull String fullCryptLine)
			throws SrtxpSecurityException {
		/*
		 * Example Input:
		 *   "123456789 AES_CM_128_HMAC_SHA1_80 inline:tGU6L4vui7vFvia/bPo6uXiXDpB/D9hY30fs4+U7|2^31|123456789:4"
		 */
		String[] tmpSplit = fullCryptLine.split(" ");
		if (tmpSplit.length < 3) {
			throw new SrtxpSecurityException("Invalid crypto attribute: '" + fullCryptLine + "'");
		}

		// Tag (max. 9 digits per RFC)
		String tmpTagStr = tmpSplit[0].strip();
		long tmpTagLong = Long.valueOf(parseUlong("Tag", tmpTagStr)).intValue();
		if (tmpTagLong > (long)SrtxpKmd.MAX_TAG_VALUE) {
			throw new SrtxpSecurityException("Invalid Tag in crypto attribute (is=" +
							Long.toUnsignedString(tmpTagLong) + ", max=" + Integer.toUnsignedString(SrtxpKmd.MAX_TAG_VALUE) + ")");
		}
		int tmpTagInt = (int)tmpTagLong;

		// Crypto-Suite
		String tmpSuite = tmpSplit[1].strip().toUpperCase();
		if (! tmpSuite.equals("AES_CM_128_HMAC_SHA1_80")) {
			throw new SrtxpSecurityException("Unsupported Crypto-Suite in crypto attribute: '" + tmpSuite + "'");
		}

		// Master Key + Salt, optional KDR, optional MKI
		String tmpInlineStr = tmpSplit[2].strip();
		if (! tmpInlineStr.toLowerCase().startsWith("inline:")) {
			throw new SrtxpSecurityException("Invalid format in crypto attribute: '" + tmpInlineStr + "' - " +
					"missing 'inline:' prefix");
		}
		tmpInlineStr = tmpInlineStr.substring("inline:".length()).strip();
		String[] tmpInlineSplit = tmpInlineStr.split("\\|");
		if (tmpInlineSplit.length == 0 || tmpInlineSplit.length > 3) {
			throw new SrtxpSecurityException("Invalid format in crypto attribute: '" + tmpInlineStr + "' - " +
					"invalid number of fields (expected 1-3)");
		}
		String tmpSdesB64 = tmpInlineSplit[0].strip();
		BufferExt tmpMkMsBe = BufferExt.decodeBase64String(tmpSdesB64);
		int expLen = SrtxpKmd.DEFAULT_ENCR_KEY_LEN + KeySizes.SALT_SIZE;
		if (tmpMkMsBe.getUsed() != expLen) {
			throw new SrtxpSecurityException("Invalid length of MasterKey and MasterSalt in crypto attribute: " +
					tmpMkMsBe.getUsed() + " bytes (exp=" + Integer.toUnsignedString(expLen) + ")");
		}
		SrtxpKdr tmpKdr = null;
		SrtxpMki tmpMki = null;
		for (int ix = 1; ix < tmpInlineSplit.length; ix++) {
			String tmpKdrOrMkiStr = tmpInlineSplit[ix].strip();
			if (tmpKdrOrMkiStr.contains(":")) {
				tmpMki = parseSdesMki(tmpKdrOrMkiStr);
				continue;
			}
			tmpKdr = parseSdesKdr(tmpKdrOrMkiStr);
		}
		if (tmpKdr == null) {
			tmpKdr = SrtxpKdr.ofEmpty();
		}
		if (tmpMki == null) {
			tmpMki = SrtxpMki.ofEmpty();
		}

		//
		SrtxpKmd resObj = new SrtxpKmd(
				true,
				tmpTagInt,
				SrtxpKmd.DEFAULT_ENCR_KEY_LEN,
				tmpMkMsBe.slice(0, SrtxpKmd.DEFAULT_ENCR_KEY_LEN),
				tmpMkMsBe.slice(SrtxpKmd.DEFAULT_ENCR_KEY_LEN),
				SrtxpKmd.DEFAULT_AUTH_KEY_LEN,
				SrtxpKmd.DEFAULT_AUTH_TAG_LEN,
				tmpMki,
				idSsrc,
				tmpKdr
			);
		return Optional.of(resObj);
	}

	private @NonNull SrtxpMki parseSdesMki(@NonNull String inpMkiStr) throws SrtxpSecurityException {
		// <MKI_value>:<MKI_length_bytes>
		String[] tmpMkiValLenSplit = inpMkiStr.split(":");
		if (tmpMkiValLenSplit.length != 2) {
			throw new SrtxpSecurityException("Invalid number of fields for MKI (expected 2) in crypto attribute: " +
					"'" + inpMkiStr + "'");
		}
		long tmpMkiValLong = parseUlong("MKI Value", tmpMkiValLenSplit[0]);
		long tmpMkiLenLong = parseUlong("MKI Length", tmpMkiValLenSplit[1]);
		if (tmpMkiLenLong != 0 && tmpMkiLenLong != 1 && tmpMkiLenLong != 2 && tmpMkiLenLong != 4 && tmpMkiLenLong != 8) {
			return SrtxpMki.ofAutoSized(tmpMkiValLong);
		}
		return SrtxpMki.of(tmpMkiValLong, (int) tmpMkiLenLong);
	}

	private @NonNull SrtxpKdr parseSdesKdr(@NonNull String inpKdrStr) throws SrtxpSecurityException {
		// Key Lifetime: format "2^DIGITS" or "INTEGER"
		long tmpKdrLong;
		if (inpKdrStr.startsWith("2^")) {
			long tmpKdrExp = parseUlong("KDR exp", inpKdrStr.substring("2^".length()));
			if (tmpKdrExp > SrtxpKmd.MAX_KDR_EXPONENT) {
				throw new SrtxpSecurityException("KDR exponent exceeds maximum allowed value (is=" +
						Long.toUnsignedString(tmpKdrExp) + ", max=" + Long.toUnsignedString(SrtxpKmd.MAX_KDR_EXPONENT) + ")");
			}
			tmpKdrLong = Math.powExact(2L, (int)tmpKdrExp);
		} else {
			tmpKdrLong = parseUlong("KDR int", inpKdrStr);
		}
		if (tmpKdrLong > SrtxpKmd.MAX_KDR_PACKETS) {
			throw new SrtxpSecurityException("KDR value exceeds maximum allowed value (is=" +
					Long.toUnsignedString(tmpKdrLong) + ", max=" + Long.toUnsignedString(SrtxpKmd.MAX_KDR_PACKETS) + ")");
		}
		return SrtxpKdr.ofAutoSized(tmpKdrLong);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String listOfStringsToString(@NonNull List<@NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (String tmpEntry : input) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(tmpEntry).append("'");
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	private static @NonNull String listOfMediaToString(@NonNull List<@NonNull RtspProtoSdpDataMediaEntry> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (RtspProtoSdpDataMediaEntry tmpEntry : input) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry);
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static long parseUlong(@NonNull String desc, @NonNull String str) throws SrtxpSecurityException {
		try {
			return Long.parseUnsignedLong(str.strip());
		} catch (NumberFormatException e) {
			throw new SrtxpSecurityException("Invalid value for " + desc + ": '" + str + "'");
		}
	}

}
