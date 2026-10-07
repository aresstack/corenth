000100 IDENTIFICATION DIVISION.                                         ACCTUPD 
000200 PROGRAM-ID. ACCTUPD.                                             ACCTUPD 
000300*SYNTHETIC FIXTURE: CALL 'COMMENTED' IS ONLY A COMMENT            ACCTUPD 
000400 DATA DIVISION.                                                   ACCTUPD 
000500 WORKING-STORAGE SECTION.                                         ACCTUPD 
000600 01  WS-PGM            PIC X(8) VALUE 'DYNAMIC1'.                 ACCTUPD 
000700 01  WS-COUNT          PIC 9(4) VALUE 0.                          ACCTUPD 
000800     COPY ACCTREC.                                                ACCTUPD 
000900     EXEC SQL INCLUDE SQLCA END-EXEC.                             ACCTUPD 
001000 PROCEDURE DIVISION.                                              ACCTUPD 
001100 MAIN SECTION.                                                    ACCTUPD 
001200 0000-START.                                                      ACCTUPD 
001300     PERFORM 1000-INIT THRU 1000-EXIT                             ACCTUPD 
001400     PERFORM 3 TIMES                                              ACCTUPD 
001500         CALL 'LOGGER' USING WS-COUNT                             ACCTUPD 
001600     END-PERFORM                                                  ACCTUPD 
001700     PERFORM UNTIL WS-COUNT > 9                                   ACCTUPD 
001800         ADD 1 TO WS-COUNT                                        ACCTUPD 
001900     END-PERFORM                                                  ACCTUPD 
002000     CALL WS-PGM                                                  ACCTUPD 
002100     DISPLAY 'CALL NOTREAL'  *> CALL 'FLOATING'                   ACCTUPD 
002200D    CALL 'DEBUGONLY'                                             ACCTUPD 
002300     EXEC CICS LINK PROGRAM('ACCTCHK') COMMAREA(WS-COUNT)         ACCTUPD 
002400     END-EXEC                                                     ACCTUPD 
002500     EXEC SQL CALL NOTACOBOLCALL END-EXEC                         ACCTUPD 
002600     CALL                                                         ACCTUPD 
002700         'ACCTPOST'                                               ACCTUPD 
002800     GOBACK.                                                      ACCTUPD 
002900 1000-INIT.                                                       ACCTUPD 
003000     MOVE 'CONTINUED LITERAL CALL XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXACCTUPD 
003100-    'X CALL ''QUOTED''' TO WS-PGM.                               ACCTUPD 
003200     CALL 'AFTERLIT'.                                             ACCTUPD 
003300 1000-EXIT.                                                       ACCTUPD 
003400     EXIT.                                                        ACCTUPD 
