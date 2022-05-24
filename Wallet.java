/** 
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
 * 
 */

/*
 */

/*
 * @(#)Wallet.java	1.11 06/01/03
 */

package com.oracle.jcclassic.samples.wallet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;

public class Wallet extends Applet {

	/* constants declaration */

	// code of CLA byte in the command APDU header
	final static byte Wallet_CLA = (byte) 0x80;

	// codes of INS byte in the command APDU header
	final static byte VERIFY = (byte) 0x20;
	final static byte GRADE = (byte) 0x30;
	final static byte GET_GRADE = (byte) 0x40;
	final static byte GET_GRADE_COUNT = (byte) 0x50;
	final static byte GET_ID = (byte) 0x60;

	// discipline codes
	final static short SD_CODE = 0;
	final static short ACSO_CODE = 1;
	final static short LOGICS_CODE = 2;
	final static short MATH_CODE = 3;
	final static short IP_CODE = 4;

	final static short MAX_GRADE = 11;
	final static short MIN_GRADE = 1;

	// maximum number of incorrect tries before the
	// PIN is blocked
	final static byte PIN_TRY_LIMIT = (byte) 0x03;
	// maximum size PIN
	final static byte MAX_PIN_SIZE = (byte) 0x08;

	// signal that the PIN verification failed
	final static short SW_VERIFICATION_FAILED = 0x6300;
	// signal the the PIN validation is required
	final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
	// signal invalid grading
	final static short SW_INVALID_GRADING = 0x6A83;

	// signal that the requested grade does not exist
	final static short SW_INEXISTENT_GRADE = 0x6A85;

	/* instance variables declaration */
	OwnerPIN pin;
	short id;
	byte[] sd_grades = new byte[20];
	byte[] sd_grades_days = new byte[20];
	byte[] sd_grades_months = new byte[20];
	byte[] sd_grades_years = new byte[20];
	short sd_grade_count = 0;
	byte[] acso_grades = new byte[20];
	byte[] acso_grades_days = new byte[20];
	byte[] acso_grades_months = new byte[20];
	byte[] acso_grades_years = new byte[20];
	short acso_grade_count = 0;
	byte[] logics_grades = new byte[20];
	byte[] logics_grades_days = new byte[20];
	byte[] logics_grades_months = new byte[20];
	byte[] logics_grades_years = new byte[20];
	short logics_grade_count = 0;
	byte[] math_grades = new byte[20];
	byte[] math_grades_days = new byte[20];
	byte[] math_grades_months = new byte[20];
	byte[] math_grades_years = new byte[20];
	short math_grade_count = 0;
	byte[] ip_grades = new byte[20];
	byte[] ip_grades_days = new byte[20];
	byte[] ip_grades_months = new byte[20];
	byte[] ip_grades_years = new byte[20];
	short ip_grade_count = 0;

	private Wallet(byte[] bArray, short bOffset, byte bLength, short myId) {

		// It is good programming practice to allocate
		// all the memory that an applet needs during
		// its lifetime inside the constructor
		pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);
		id = myId;

		byte iLen = bArray[bOffset]; // aid length
		bOffset = (short) (bOffset + iLen + 1);
		byte cLen = bArray[bOffset]; // info length
		bOffset = (short) (bOffset + cLen + 1);
		byte aLen = bArray[bOffset]; // applet data length

		// The installation parameters contain the PIN
		// initialization value
		pin.update(bArray, (short) (bOffset + 1), aLen);
		register();

	} // end of the constructor

	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// create a Wallet applet instance
		new Wallet(bArray, bOffset, bLength, (short) 13);
	} // end of install method

	@Override
	public boolean select() {

		// The applet declines to be selected
		// if the pin is blocked.
		if (pin.getTriesRemaining() == 0) {
			return false;
		}

		return true;

	}// end of select method

	@Override
	public void deselect() {
		// reset the pin value
		pin.reset();

	}

	@Override
	public void process(APDU apdu) {

		// APDU object carries a byte array (buffer) to
		// transfer incoming and outgoing APDU header
		// and data bytes between card and CAD

		// At this point, only the first header bytes
		// [CLA, INS, P1, P2, P3] are available in
		// the APDU buffer.
		// The interface javacard.framework.ISO7816
		// declares constants to denote the offset of
		// these bytes in the APDU buffer

		byte[] buffer = apdu.getBuffer();
		// check SELECT APDU command

		if (apdu.isISOInterindustryCLA()) {
			if (buffer[ISO7816.OFFSET_INS] == (byte) (0xA4)) {
				return;
			}
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}

		// verify the reset of commands have the
		// correct CLA byte, which specifies the
		// command structure
		if (buffer[ISO7816.OFFSET_CLA] != Wallet_CLA) {
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}

		switch (buffer[ISO7816.OFFSET_INS]) {
		case VERIFY:
			verify(apdu);
			return;
		case GRADE:
			grade(apdu);
			return;
		case GET_GRADE:
			getGrade(apdu);
			return;
		case GET_GRADE_COUNT:
			getGradeCount(apdu);
			return;
		case GET_ID:
			getID(apdu);
			return;
		default:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}

	} // end of process method

	private void grade(APDU apdu) {
		// access authentication
		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}

		byte[] buffer = apdu.getBuffer();
		// vom pasa nota acordata, respectiv disciplina la care a fost acordata este cea
		// curenta in P1 si P2
		byte grade = buffer[ISO7816.OFFSET_P1];
		byte objectCode = buffer[ISO7816.OFFSET_P2];

		// Lc byte denotes the number of bytes in the
		// data field of the command APDU
		byte numBytes = buffer[ISO7816.OFFSET_LC];

		// indicate that this APDU has incoming data
		// and receive data starting from the offset
		// ISO7816.OFFSET_CDATA following the 5 header
		// bytes.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());

		// it is an error if the number of data bytes
		// read does not match the number in Lc byte
		// citim 6 bytes pentru data - 2 pentru zi, 2 pentru luna, 2 pentru an
		if ((numBytes != 6) || (byteRead != 6)) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		// get the credit amount
		short currentDay = buffer[ISO7816.OFFSET_CDATA];
		short currentMonth = buffer[ISO7816.OFFSET_CDATA + 2];
		short currentYear = buffer[ISO7816.OFFSET_CDATA + 4];

		// verificam daca nota oferita si codul disciplinei acesteia sunt in parametrii
		// stabiliti
		// check the credit amount
		if ((grade > MAX_GRADE) || (grade < MIN_GRADE) || (objectCode < 0) || (objectCode > 4)) {
			ISOException.throwIt(SW_INVALID_GRADING);
		}

		switch (objectCode) {
		case 0:
			sd_grades[(short) (sd_grade_count * 2)] = (byte) (grade >> 8);
			sd_grades[(short) (sd_grade_count * 2 + 1)] = (byte) (grade & 0xFF);
			sd_grades_days[(short) (sd_grade_count * 2)] = (byte) (currentDay >> 8);
			sd_grades_days[(short) (sd_grade_count * 2 + 1)] = (byte) (currentDay & 0xFF);
			sd_grades_months[(short) (sd_grade_count * 2)] = (byte) (currentMonth >> 8);
			sd_grades_months[(short) (sd_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			sd_grades_years[(short) (sd_grade_count * 2)] = (byte) (currentYear >> 8);
			sd_grades_years[(short) (sd_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			sd_grade_count = (short) (sd_grade_count + 1);
			break;
		case 1:
			acso_grades[(short) (acso_grade_count * 2)] = (byte) (grade >> 8);
			acso_grades[(short) (acso_grade_count * 2 + 1)] = (byte) (grade & 0xFF);
			acso_grades_days[(short) (acso_grade_count * 2)] = (byte) (currentDay >> 8);
			acso_grades_days[(short) (acso_grade_count * 2 + 1)] = (byte) (currentDay & 0xFF);
			acso_grades_months[(short) (acso_grade_count * 2)] = (byte) (currentMonth >> 8);
			acso_grades_months[(short) (acso_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			acso_grades_years[(short) (acso_grade_count * 2)] = (byte) (currentYear >> 8);
			acso_grades_years[(short) (acso_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			acso_grade_count = (short) (acso_grade_count + 1);
			break;
		case 2:
			logics_grades[(short) (logics_grade_count * 2)] = (byte) (grade >> 8);
			logics_grades[(short) (logics_grade_count * 2 + 1)] = (byte) (grade & 0xFF);
			logics_grades_days[(short) (logics_grade_count * 2)] = (byte) (currentDay >> 8);
			logics_grades_days[(short) (logics_grade_count * 2 + 1)] = (byte) (currentDay & 0xFF);
			logics_grades_months[(short) (logics_grade_count * 2)] = (byte) (currentMonth >> 8);
			logics_grades_months[(short) (logics_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			logics_grades_years[(short) (logics_grade_count * 2)] = (byte) (currentYear >> 8);
			logics_grades_years[(short) (logics_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			logics_grade_count = (short) (logics_grade_count + 1);
			break;
		case 3:
			math_grades[(short) (math_grade_count * 2)] = (byte) (grade >> 8);
			math_grades[(short) (math_grade_count * 2 + 1)] = (byte) (grade & 0xFF);
			math_grades_days[(short) (math_grade_count * 2)] = (byte) (currentDay >> 8);
			math_grades_days[(short) (math_grade_count * 2 + 1)] = (byte) (currentDay & 0xFF);
			math_grades_months[(short) (math_grade_count * 2)] = (byte) (currentMonth >> 8);
			math_grades_months[(short) (math_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			math_grades_years[(short) (math_grade_count * 2)] = (byte) (currentYear >> 8);
			math_grades_years[(short) (math_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			math_grade_count = (short) (math_grade_count + 1);
			break;
		case 4:
			ip_grades[(short) (ip_grade_count * 2)] = (byte) (grade >> 8);
			ip_grades[(short) (ip_grade_count * 2 + 1)] = (byte) (grade & 0xFF);
			ip_grades_days[(short) (ip_grade_count * 2)] = (byte) (currentDay >> 8);
			ip_grades_days[(short) (ip_grade_count * 2 + 1)] = (byte) (currentDay & 0xFF);
			ip_grades_months[(short) (ip_grade_count * 2)] = (byte) (currentMonth >> 8);
			ip_grades_months[(short) (ip_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			ip_grades_years[(short) (ip_grade_count * 2)] = (byte) (currentYear >> 8);
			ip_grades_years[(short) (ip_grade_count * 2 + 1)] = (byte) (currentMonth & 0xFF);
			ip_grade_count = (short) (ip_grade_count + 1);
			break;
		default:
			ISOException.throwIt(SW_INVALID_GRADING);
		}
		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		short le = apdu.setOutgoing();

		if (le < 2) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		// informs the CAD the actual number of bytes
		// returned
		apdu.setOutgoingLength((byte) 2);
		buffer[0] = (byte) (id >> 8);
		buffer[1] = (byte) (id & 0xFF);
		apdu.sendBytes((short) 0, (short) 2);

	} // end of grade method

	private void getGrade(APDU apdu) {
		// access authentication
		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		byte[] buffer = apdu.getBuffer();
		// vom pasa disciplina si nota ceruta in P1 si P2
		byte discipline = buffer[ISO7816.OFFSET_P1];
		byte index = buffer[ISO7816.OFFSET_P2];

		// verificam daca nota oferita si codul disciplinei acesteia sunt in parametrii
		// stabiliti
		if ((discipline < 0) || (discipline > 4)) {
			ISOException.throwIt(SW_INEXISTENT_GRADE);
		}

		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		short le = apdu.setOutgoing();

		if (le < 2) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		switch (discipline) {
		case 0:
			if ((index < 0) || (index > sd_grade_count)) {
				ISOException.throwIt(SW_INEXISTENT_GRADE);
			}
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = sd_grades[(short) (index * 2)];
			buffer[1] = sd_grades[(short) (index * 2 + 1)];
			apdu.sendBytes((short) 0, (short) 2);
			break;
		case 1:
			if ((index < 0) || (index > acso_grade_count)) {
				ISOException.throwIt(SW_INEXISTENT_GRADE);
			}
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = acso_grades[(short) (index * 2)];
			buffer[1] = acso_grades[(short) (index * 2 + 1)];
			apdu.sendBytes((short) 0, (short) 2);
			break;
		case 2:
			if ((index < 0) || (index > logics_grade_count)) {
				ISOException.throwIt(SW_INEXISTENT_GRADE);
			}
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = logics_grades[(short) (index * 2)];
			buffer[1] = logics_grades[(short) (index * 2 + 1)];
			apdu.sendBytes((short) 0, (short) 2);
			break;
		case 3:
			if ((index < 0) || (index > math_grade_count)) {
				ISOException.throwIt(SW_INEXISTENT_GRADE);
			}
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = math_grades[(short) (index * 2)];
			buffer[1] = math_grades[(short) (index * 2 + 1)];
			apdu.sendBytes((short) 0, (short) 2);
			break;
		case 4:
			if ((index < 0) || (index > ip_grade_count)) {
				ISOException.throwIt(SW_INEXISTENT_GRADE);
			}
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = ip_grades[(short) (index * 2)];
			buffer[1] = ip_grades[(short) (index * 2 + 1)];
			apdu.sendBytes((short) 0, (short) 2);
			break;
		default:
			ISOException.throwIt(SW_INEXISTENT_GRADE);
		}
	} // end of getGrade method

	private void getID(APDU apdu) {
		// access authentication
		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		byte[] buffer = apdu.getBuffer();

		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		short le = apdu.setOutgoing();

		if (le < 2) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		apdu.setOutgoingLength((byte) 2);
		buffer[0] = (byte) (id >> 8);
		buffer[1] = (byte) (id & 0xFF);
		apdu.sendBytes((short) 0, (short) 2);
	} // end of getID method

	private void getGradeCount(APDU apdu) {
		// access authentication
		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		byte[] buffer = apdu.getBuffer();
		// vom pasa disciplina ceruta in P1
		byte discipline = buffer[ISO7816.OFFSET_P1];

		// verificam daca, codul disciplinei este in parametrii
		// stabiliti
		if ((discipline < 0) || (discipline > 4)) {
			ISOException.throwIt(SW_INEXISTENT_GRADE);
		}

		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		short le = apdu.setOutgoing();

		if (le < 2) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}

		switch (discipline) {
		case 0:
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = (byte) (sd_grade_count >> 8);
			buffer[1] = (byte) (sd_grade_count & 0xFF);
			apdu.sendBytes((short) 0, (short) 2);
			return;
		case 1:
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = (byte) (acso_grade_count >> 8);
			buffer[1] = (byte) (acso_grade_count & 0xFF);
			apdu.sendBytes((short) 0, (short) 2);
			return;
		case 2:
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = (byte) (logics_grade_count >> 8);
			buffer[1] = (byte) (logics_grade_count & 0xFF);
			apdu.sendBytes((short) 0, (short) 2);
			return;
		case 3:
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = (byte) (math_grade_count >> 8);
			buffer[1] = (byte) (math_grade_count & 0xFF);
			apdu.sendBytes((short) 0, (short) 2);
			return;
		case 4:
			apdu.setOutgoingLength((byte) 2);
			buffer[0] = (byte) (ip_grade_count >> 8);
			buffer[1] = (byte) (ip_grade_count & 0xFF);
			apdu.sendBytes((short) 0, (short) 2);
			return;
		default:
			ISOException.throwIt(SW_INEXISTENT_GRADE);
		}
	} // end of getGradeCount method

	private void verify(APDU apdu) {

		byte[] buffer = apdu.getBuffer();
		// retrieve the PIN data for validation.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());

		// check pin
		// the PIN data is read into the APDU buffer
		// at the offset ISO7816.OFFSET_CDATA
		// the PIN data length = byteRead
		if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead) == false) {
			ISOException.throwIt(SW_VERIFICATION_FAILED);
		}

	} // end of validate method
} // end of class Wallet
