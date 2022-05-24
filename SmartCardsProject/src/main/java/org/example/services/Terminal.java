package org.example.services;

import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadClientInterface;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;
import org.example.database.dao.DisciplineDAO;
import org.example.database.dao.NoteDAO;
import org.example.database.dao.StudentsDAO;
import org.example.database.entities.Disciplina;
import org.example.database.entities.Nota;
import org.example.database.entities.Student;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Terminal {
    private static final Scanner scanner = new Scanner(System.in);

    private static final String capWalletPath = "C:\\Program Files (x86)\\Oracle\\Java Card Development Kit Simulator 3.1.0\\samples\\classic_applets\\Wallet\\applet\\apdu_scripts\\cap-Wallet.script";
    private static final String initializationScriptPath = "C:\\Users\\adria\\Desktop\\SmartCardsProject\\src\\main\\java\\org\\example\\services\\initializeStudentCard.scr";

    private final static byte Wallet_CLA = (byte) 0x80;

    private final static byte VERIFY = (byte) 0x20;
    private final static byte GRADE = (byte) 0x30;
    private final static byte GET_GRADE = (byte) 0x40;
    private final static byte GET_GRADE_COUNT = (byte) 0x50;
    private final static byte GET_ID = (byte) 0x60;

    private final static byte SD_CODE = 0;
    private final static byte ACSO_CODE = 1;
    private final static byte LOGICS_CODE = 2;
    private final static byte MATH_CODE = 3;
    private final static byte IP_CODE = 4;

    private final DisciplineDAO disciplineDAO;
    private final NoteDAO noteDAO;
    private final StudentsDAO studentsDAO;

    @Autowired
    public Terminal(DisciplineDAO disciplineDAO, NoteDAO noteDAO, StudentsDAO studentsDAO) {
        this.disciplineDAO = disciplineDAO;
        this.noteDAO = noteDAO;
        this.studentsDAO = studentsDAO;
    }

    public void run() {
        String crefFilePath = "C:\\Program Files (x86)\\Oracle\\Java Card Development Kit Simulator 3.1.0\\bin\\cref.bat";
        try {
            Runtime.getRuntime().exec(crefFilePath);
        } catch (IOException ex) {
            System.out.println("Something went wrong! IOException caught!");
        }
        CadClientInterface cad;
        Socket sock;
        InputStream is = null;
        OutputStream os = null;
        try {
            sock = new Socket("localhost", 9025);
            is = sock.getInputStream();
            os = sock.getOutputStream();
        } catch (IOException ex) {
            System.out.println("Something went wrong! IOException caught!");
        }
        cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, is, os);

        try {
            cad.powerUp();
            initialize(cad);
            commandCentre(cad);
            cad.powerDown();
        } catch (IOException ex) {
            System.out.println("Something went wrong! IOException caught!");
        } catch (CadTransportException ex) {
            System.out.println("Something went wrong! CadTransportException caught!");
        }
    }

    private void commandCentre(CadClientInterface cadClientInterface) throws IOException, CadTransportException {
        boolean exited = false;
        boolean verifyingPhase;
        while (!exited) {
            System.out.println("'grade' -> grade a student\n'getgrades' -> get student's grade\n'pay' -> pay for student fee\n'exit' -> exit the Terminal!\nInsert the command below!");
            String command = scanner.nextLine().toLowerCase();
            switch (command) {
                case "grade":

                    // GRADING VERIFY PHASE

                    verifyingPhase = true;
                    int remainingTries = 3;
                    boolean verificationSuccessful = false;
                    while (verifyingPhase) {
                        System.out.println("Student, please insert your PIN!");
                        String currentPIN = scanner.nextLine();
                        System.out.println("Inserted PIN value is: " + currentPIN);
                        if (verify(cadClientInterface, currentPIN)) {
                            remainingTries--;
                            System.out.println("Incorrect PIN! (" + remainingTries + ") tries remaining!");
                            if (remainingTries == 0) {
                                System.out.println("Out of attempts! Terminating!");
                                verifyingPhase = false;
                                exited = true;
                            }
                        } else {
                            System.out.println("Verifying step completed!");
                            verifyingPhase = false;
                            verificationSuccessful = true;
                        }
                    }

                    // GRADING PHASE

                    if (verificationSuccessful) {
                        System.out.println("Professor, please insert the grade!");
                        String gradeStr = scanner.nextLine();
                        int grade = Integer.parseInt(gradeStr);
                        if (grade > 10 || grade < 0) {
                            System.out.println("Not a valid grade!");
                            break;
                        }
                        byte gradeByte = (byte) grade;
                        System.out.println("Please insert the code of the desired discipline! 0 - SD ; 1 - ACSO ; 2 - LOGICS ; 3 - MATH ; 4 - IP");
                        String currentDiscipline = scanner.nextLine();
                        int returnedId;
                        byte currentDisciplineByte = (byte) 0x05;
                        switch (currentDiscipline) {
                            case "0":
                                currentDisciplineByte = SD_CODE;
                                break;
                            case "1":
                                currentDisciplineByte = ACSO_CODE;
                                break;
                            case "2":
                                currentDisciplineByte = LOGICS_CODE;
                                break;
                            case "3":
                                currentDisciplineByte = MATH_CODE;
                                break;
                            case "4":
                                currentDisciplineByte = IP_CODE;
                                break;
                            default:
                                System.out.println("Not a valid discipline code!");
                        }

                        // CHECK IF ERROR CODE MUST BE REGISTERED

                        if (currentDisciplineByte != (byte) 0x05) {
                            int studentID = getID(cadClientInterface);
                            Optional<Student> student = studentsDAO.findById(studentID);
                            int gradeCountSCA = getGradeCount(cadClientInterface, currentDisciplineByte);
                            int gradeCountDB;
                            if (student.isPresent()) {
                                gradeCountDB = noteDAO.findByStudentIdAndDisciplinaId(student.get().getId(), Integer.parseInt(currentDiscipline)).size();
                                if (gradeCountDB != gradeCountSCA) {
                                    throw new RuntimeException("Unexpected error occurred!");
                                }
                                if (gradeCountSCA == 2) {
                                    int grade1 = getGrade(cadClientInterface, currentDisciplineByte, (byte) 0x00);
                                    int grade2 = getGrade(cadClientInterface, currentDisciplineByte, (byte) 0x01);
                                    if (grade1 < 5 && grade2 < 5 && !student.get().isReexaminarePlatita()) {
                                        System.out.println("You have not paid your fee! Error code registered!");
                                        gradeByte = (byte) 0x0B;
                                        grade = 11;
                                    }
                                } else {
                                    if (gradeCountSCA > 2) {
                                        System.out.println("You already have 3 grades!");
                                        break;
                                    }
                                }
                            } else {
                                throw new RuntimeException("Unexpected error occurred!");
                            }

                            // COMMIT CHANGES
                            returnedId = grade(cadClientInterface, currentDisciplineByte, gradeByte);

                            if (returnedId != -1) {
                                System.out.println("Success! The card of the student with ID (" + returnedId + ") has been updated! Now updating database and returning to menu!");
                                Nota nota = new Nota();
                                nota.setNota(grade);
                                nota.setStudentId(returnedId);
                                nota.setDisciplinaId(Integer.parseInt(currentDiscipline));
                                nota.setData(new Date());
                                noteDAO.save(nota);
                            } else {
                                System.out.println("Something went wrong with your command! Returning to menu!");
                            }
                        }
                    }
                    break;
                case "getgrades":

                    // VERIFY PHASE

                    verifyingPhase = true;
                    int remainingTries0 = 3;
                    boolean verificationSuccessful0 = false;
                    while (verifyingPhase) {
                        System.out.println("Student, please insert your PIN!");
                        String currentPIN = scanner.nextLine();
                        System.out.println("Inserted PIN value is: " + currentPIN);
                        if (verify(cadClientInterface, currentPIN)) {
                            remainingTries0--;
                            System.out.println("Incorrect PIN! (" + remainingTries0 + ") tries remaining!");
                            if (remainingTries0 == 0) {
                                System.out.println("Out of attempts! Terminating!");
                                verifyingPhase = false;
                                exited = true;
                            }
                        } else {
                            System.out.println("Verifying step completed!");
                            verifyingPhase = false;
                            verificationSuccessful0 = true;
                        }
                    }

                    // GETGRADES PHASE

                    if (verificationSuccessful0) {
                        System.out.println("Please insert the code of the desired discipline! 0 - SD ; 1 - ACSO ; 2 - LOGICS ; 3 - MATH ; 4 - IP");
                        String currentDiscipline0 = scanner.nextLine();
                        byte currentDisciplineByte0 = (byte) 0x05;
                        switch (currentDiscipline0) {
                            case "0":
                                currentDisciplineByte0 = SD_CODE;
                                break;
                            case "1":
                                currentDisciplineByte0 = ACSO_CODE;
                                break;
                            case "2":
                                currentDisciplineByte0 = LOGICS_CODE;
                                break;
                            case "3":
                                currentDisciplineByte0 = MATH_CODE;
                                break;
                            case "4":
                                currentDisciplineByte0 = IP_CODE;
                                break;
                            default:
                                System.out.println("Not a valid discipline code!");
                        }
                        if (currentDisciplineByte0 != (byte) 0x05) {
                            int studentID = getID(cadClientInterface);
                            Optional<Student> student = studentsDAO.findById(studentID);
                            int gradeCountSCA0 = getGradeCount(cadClientInterface, currentDisciplineByte0);
                            int gradeCountDB0;
                            if (student.isPresent()) {
                                gradeCountDB0 = noteDAO.findByStudentIdAndDisciplinaId(student.get().getId(), Integer.parseInt(currentDiscipline0)).size();
                                if (gradeCountDB0 != gradeCountSCA0) {
                                    System.out.println(gradeCountDB0 + " - " + gradeCountSCA0);
                                    throw new RuntimeException("Unexpected error occurred!");
                                }
                                System.out.println("You have (" + gradeCountSCA0 + ") grade(s) on your card and on the database!");
                                if (student.get().isReexaminarePlatita()) {
                                    System.out.println("You have paid your tax!");
                                } else {
                                    System.out.println("You have not paid your tax!");
                                }
                                List<Integer> noteDB = noteDAO.findByStudentIdAndDisciplinaId(studentID, Integer.parseInt(currentDiscipline0)).stream().map(Nota::getNota).collect(Collectors.toList());
                                List<Integer> noteSCA = new LinkedList<>();
                                for (int i = 0; i < gradeCountSCA0; i++) {
                                    noteSCA.add(getGrade(cadClientInterface, currentDisciplineByte0, (byte) i));
                                }
                                System.out.println("Note din DB: " + noteDB);
                                System.out.println("Note din SCA: " + noteSCA);
                            } else {
                                throw new RuntimeException("Unexpected error occurred!");
                            }
                        }
                    }
                    break;
                case "pay":

                    // VERIFY PHASE

                    verifyingPhase = true;
                    int remainingTries1 = 3;
                    boolean verificationSuccessful1 = false;
                    while (verifyingPhase) {
                        System.out.println("Student, please insert your PIN!");
                        String currentPIN = scanner.nextLine();
                        System.out.println("Inserted PIN value is: " + currentPIN);
                        if (verify(cadClientInterface, currentPIN)) {
                            remainingTries1--;
                            System.out.println("Incorrect PIN! (" + remainingTries1 + ") tries remaining!");
                            if (remainingTries1 == 0) {
                                System.out.println("Out of attempts! Terminating!");
                                verifyingPhase = false;
                                exited = true;
                            }
                        } else {
                            System.out.println("Verifying step completed!");
                            verifyingPhase = false;
                            verificationSuccessful1 = true;
                        }
                    }

                    // PAY TAX PHASE

                    if (verificationSuccessful1) {
                        payTax(cadClientInterface);
                    }
                    break;
                case "exit":
                    System.out.println("Exiting the Terminal!");
                    exited = true;
                    break;
                default:
                    System.out.println("Invalid command!");
            }
        }
    }

    private boolean verify(CadClientInterface cadClientInterface, String currentPIN) throws IOException, CadTransportException {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = Wallet_CLA;
        apdu.command[Apdu.INS] = VERIFY;
        apdu.command[Apdu.P1] = (byte) 0x00;
        apdu.command[Apdu.P2] = (byte) 0x00;
        int lc = currentPIN.length();
        int dataIndex = 0;
        byte[] data = new byte[lc];
        List<Byte> pinBytes = currentPIN.codePoints().mapToObj(c -> String.valueOf((char) c)).map(byteString -> (byte) Integer.parseInt(byteString, 16)).collect(Collectors.toList());
        for (byte b : pinBytes) {
            data[dataIndex] = b;
            dataIndex++;
        }
        apdu.setDataIn(data, lc);
        apdu.setLe(127);
        System.out.println("APDU command is: " + apdu);
        cadClientInterface.exchangeApdu(apdu);
        System.out.println("APDU response is: " + apdu);
        String hexSW1 = Integer.toHexString(apdu.sw1sw2[0] & 0xFF);
        return !hexSW1.equals("90");
    }

    private int grade(CadClientInterface cadClientInterface, byte disciplineCode, byte nota) throws IOException, CadTransportException {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = Wallet_CLA;
        apdu.command[Apdu.INS] = GRADE;
        apdu.command[Apdu.P1] = nota;
        apdu.command[Apdu.P2] = disciplineCode;

        int year = Calendar.getInstance().get(Calendar.YEAR);
        byte monthBytes = (byte) Calendar.getInstance().get(Calendar.MONTH);
        byte dayBytes = (byte) Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        byte[] yearBytes = ByteBuffer.allocate(2).putShort((short) year).array();

        int lc = 6;
        byte[] data = new byte[lc];
        data[0] = dayBytes;
        data[1] = (byte) 0x00;
        data[2] = monthBytes;
        data[3] = (byte) 0x00;
        data[4] = yearBytes[0];
        data[5] = yearBytes[1];
        apdu.setDataIn(data, lc);
        apdu.setLe(2);
        System.out.println("APDU command is: " + apdu);
        cadClientInterface.exchangeApdu(apdu);
        System.out.println("APDU response is: " + apdu);
        String hexSW1 = Integer.toHexString(apdu.sw1sw2[0] & 0xFF);
        if (hexSW1.equals("90"))
            return apdu.getDataOut()[1] & 0xFF;
        else
            return -1;
    }

    private int getGrade(CadClientInterface cadClientInterface, byte disciplineCode, byte index) throws IOException, CadTransportException {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = Wallet_CLA;
        apdu.command[Apdu.INS] = GET_GRADE;
        apdu.command[Apdu.P1] = disciplineCode;
        apdu.command[Apdu.P2] = index;

        apdu.setLc(0);
        apdu.setLe(2);
        System.out.println("APDU command is: " + apdu);
        cadClientInterface.exchangeApdu(apdu);
        System.out.println("APDU response is: " + apdu);
        return apdu.getDataOut()[1] & 0xFF;
    }

    private int getGradeCount(CadClientInterface cadClientInterface, byte disciplineCode) throws IOException, CadTransportException {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = Wallet_CLA;
        apdu.command[Apdu.INS] = GET_GRADE_COUNT;
        apdu.command[Apdu.P1] = disciplineCode;
        apdu.command[Apdu.P2] = (byte) 0x00;

        apdu.setLc(0);
        apdu.setLe(2);
        System.out.println("APDU command is: " + apdu);
        cadClientInterface.exchangeApdu(apdu);
        System.out.println("APDU response is: " + apdu);
        return apdu.getDataOut()[1] & 0xFF;
    }

    private int getID(CadClientInterface cadClientInterface) throws IOException, CadTransportException {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = Wallet_CLA;
        apdu.command[Apdu.INS] = GET_ID;
        apdu.command[Apdu.P1] = (byte) 0x00;
        apdu.command[Apdu.P2] = (byte) 0x00;

        apdu.setLc(0);
        apdu.setLe(2);
        System.out.println("APDU command is: " + apdu);
        cadClientInterface.exchangeApdu(apdu);
        System.out.println("APDU response is: " + apdu);
        return apdu.getDataOut()[1] & 0xFF;
    }

    private void payTax(CadClientInterface cadClientInterface) throws IOException, CadTransportException {
        int studentID = getID(cadClientInterface);
        Optional<Student> student = studentsDAO.findById(studentID);
        if (student.isPresent()) {
            Student paidStudent = student.get();
            paidStudent.setReexaminarePlatita(true);
            System.out.println("Your tax has been paid!");
        } else {
            throw new RuntimeException("Unexpected error occurred!");
        }
    }

    private void initialize(CadClientInterface cad) throws IOException, CadTransportException {
        System.out.println("CAP WALLET script");
        parseScript(cad, capWalletPath);
        System.out.println("INITIALIZATION script");
        parseScript(cad, initializationScriptPath);
        Disciplina SD = new Disciplina((int) SD_CODE, "SD");
        disciplineDAO.save(SD);
        Disciplina ACSO = new Disciplina((int) ACSO_CODE, "ACSO");
        disciplineDAO.save(ACSO);
        Disciplina LOGICS = new Disciplina((int) LOGICS_CODE, "LOGICS");
        disciplineDAO.save(LOGICS);
        Disciplina MATH = new Disciplina((int) MATH_CODE, "MATH");
        disciplineDAO.save(MATH);
        Disciplina IP = new Disciplina((int) IP_CODE, "IP");
        disciplineDAO.save(IP);

        Student student = new Student(13, "Adrian", false);
        studentsDAO.save(student);
    }

    private void parseScript(CadClientInterface cadClientInterface, String path) throws IOException, CadTransportException {
        List<String> scriptLines = Files.readAllLines(Paths.get(path)).stream().filter(line -> line.startsWith("0x")).map(line -> line.replace(";", "")).collect(Collectors.toList());
        for (String line : scriptLines) {
            Apdu apdu = new Apdu();
            List<Byte> bytes = Arrays.stream(line.split(" ")).map(byteString -> (byte) Integer.parseInt(byteString.substring(2), 16)).collect(Collectors.toList());
            System.out.println(bytes);
            apdu.command[Apdu.CLA] = bytes.get(0);
            apdu.command[Apdu.INS] = bytes.get(1);
            apdu.command[Apdu.P1] = bytes.get(2);
            apdu.command[Apdu.P2] = bytes.get(3);
            int lc = bytes.get(4).intValue();
            if (lc > 0) {
                byte[] data = new byte[lc];
                int dataIndex = 0;
                for (byte b : bytes.subList(5, 5 + lc)) {
                    data[dataIndex] = b;
                    dataIndex++;
                }
                apdu.setDataIn(data, lc);
            } else {
                apdu.setLc(0);
            }

            apdu.setLe(bytes.get(bytes.size() - 1).intValue());
            System.out.println("APDU command is: " + apdu);
            cadClientInterface.exchangeApdu(apdu);
            System.out.println("APDU response is: " + apdu);
        }
    }
}
