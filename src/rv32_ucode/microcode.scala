//**************************************************************************
// RISCV Micro-Code
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28
//
// Micro-code that controls the processor is found in here. The Micro-code
// Compiler takes the micro-code in here and generates a ROM for use by the
// control path.
//
//--------------------------------------------------------------------------
//      Quick tutorial on writing microcode:
//    
//      Here is the example microcode for the ADD macro-instruction.  Label the
//      FIRST micro-op with the name of the macro-instruction.  It MUST match the
//      name of the macro-instruction as provided in "instructions.scala", since
//      this match is used by the MicrocodeCompiler to generate the
//      UopDispatchTable. 
//
//      The last executed micro-op should micro-jump (UBR_J) back to FETCH to
//      begin fetching the next instruction. If need be, you can add your own
//      labels in front of any micro-op, and you can use your new label as a
//      target to micro-branch to (your own personal labels do NOT have any
//      naming restrictions. Only the FIRST micro-op must be labeled with a
//      name that matches the macro-instruction name you wish to match to on
//      instruction dispatch).
//
//                            
//   /* ADD              */
//   /* A  <- Reg[rs1]   */,Label("ADD"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
//   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
//   /* Reg[rd] <- A + B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
//             ^               ^                                             ^
//        Psuedocode       Label (acts like a goto target in C or asm).      |                                                                                         ^
//                                                                       The control signals are concatenated together here.                                           |
//                                                                                                                                                            This is the micro-
//                                                                                                                                                               branch target.
//                                                                                                                                                          Use "X" for "Don't Care".
//                                                                                                                                                           Otherwise, must match  
//                                                                                                                                                            an existing Label().
//--------------------------------------------------------------------------
//       

package Sodor
{

import Chisel._
import Node._

import Constants._
 
 
object Microcode 
{

   val codes = Array[MicroOp](
                         /*      State     |          | LD_IR | Reg   | Reg  | En   | Ld A | Ld B |   ALUOP    | En   | Ld MA | Mem  | Mem  | Im  | En   |  UBr | NextState */
                         /*                |          |       |  Sel  |  Wr  | Reg  |      |      |            | ALU  |       |  Wr  |  En  | Sel | Imm  |      |           */
  
  /* --- Misc. Operations -------------------------- */
  
   /* Instruction Fetch*/
   /* MA <- PC         */ Label("FETCH"),   Signals(Cat(LDIR_X, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_1, MWR_X, MEN_0, IS_X, IEN_0, UBR_N), "X")
   /* A  <- PC         */ 
   /* IR <- Mem        */,                  Signals(Cat(LDIR_1, RS_X  , RWR_X, REN_0, LDA_0, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, IS_X, IEN_0, UBR_S), "X")
   /* PC <- A + 4      */,                  Signals(Cat(LDIR_0, RS_PC , RWR_1, REN_1, LDA_0, LDB_X, ALU_INC_A_4, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X, IEN_0, UBR_D), "X")
   /*Dispatch on Opcode*/ 
    
   /* NOP              */
   /* UBr to FETCH     */,Label("NOP"),     Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X, IEN_0, UBR_J), "FETCH")
  

   /* ILLEGAL-OP       */
   /* UBr to FETCH     */,Label("ILLEGAL"), Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X, IEN_0, UBR_J), "FETCH")
                  
   /* UNIMPLEMENTED    */
   /* UBr to FETCH     */,Label("UNIMP"),   Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X, IEN_0, UBR_J), "FETCH")
   
   
   /* Initialize PC Reg*/
   /* PC <- START_ADDR */,Label("INIT_PC"), Signals(Cat(LDIR_0, RS_PC , RWR_1, REN_1, LDA_X, LDB_X, ALU_INIT_PC, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X, IEN_0, UBR_J), "FETCH")

   /* --- Load & Store Instructions ----------------- */
       
   /* LW               */
   /* A  <- Reg[rs1]   */,Label("LW"),      Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, IS_X  , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
   // extra LW4 uop required since we can't both ujump to fetch0 or spin on LW3
   
   /* SW               */
   /* A  <- Reg[rs1]   */,Label("SW"),      Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B <- Sext(SImm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_BS , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_0, ALU_ADD   , AEN_1, LDMA_1, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Mem <- Reg[rs2]  */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_X, LDB_X, ALU_X     , AEN_0, LDMA_0, MWR_1, MEN_1, IS_X  , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
   /* SImm12 is a "split immediate" */
                                       
   
   /* --- Atomic Memory Operation Instructions ------ */
   
   
   /* --- Integer Register-Immediate Instructions --- */
 
   /* LUI              */
   /* Reg[rd]<- Imm20  */,Label("LUI"),     Signals(Cat(LDIR_0, RS_RD,  RWR_1, REN_1, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_L ,  IEN_1, UBR_J), "FETCH")
                                      
   /* ADDI             */
   /* A  <- Reg[rs1]   */,Label("ADDI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                              
   /* SLTI             */
   /* A  <- Reg[rs1]   */,Label("SLTI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- $A<$B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLT  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                              
   /* SLTIU            */
   /* A  <- Reg[rs1]   */,Label("SLTIU"),   Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A < B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLTU , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                     
   /* SLLI             */
   /* A  <- Reg[rs1]   */,Label("SLLI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A << B*/,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLL  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                
   /* SRLI             */
   /* A  <- Reg[rs1]   */,Label("SRLI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SRL  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                               
   /* SRAI             */
   /* A  <- Reg[rs1]   */,Label("SRAI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SRA  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                             
   /* ANDI             */
   /* A  <- Reg[rs1]   */,Label("ANDI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A & B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_AND  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                              
   /* ORI              */
   /* A  <- Reg[rs1]   */,Label("ORI"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A | B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_OR   , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                             
   /* XORI             */
   /* A  <- Reg[rs1]   */,Label("XORI"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A ^ B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_XOR  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
              
   /* --- Integer Register-Register Instructions ---- */
                         
   /* ADD              */
   /* A  <- Reg[rs1]   */,Label("ADD"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                        
   /* SUB              */
   /* A  <- Reg[rs1]   */,Label("SUB"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A - B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SUB  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                              
   /* SLT              */
   /* A  <- Reg[rs1]   */,Label("SLT"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- $A<$B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLT  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                              
   /* SLTU             */
   /* A  <- Reg[rs1]   */,Label("SLTU"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A < B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLTU , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                          
   /* SLL              */
   /* A  <- Reg[rs1]   */,Label("SLL"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A << B*/,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SLL  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                          
   /* SRL              */
   /* A  <- Reg[rs1]   */,Label("SRL"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SRL  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                         
   /* SRA              */
   /* A  <- Reg[rs1]   */,Label("SRA"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_SRA  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                       
   /* AND              */
   /* A  <- Reg[rs1]   */,Label("riscvAND"),Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A & B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_AND  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                        
   /* OR               */
   /* A  <- Reg[rs1]   */,Label("riscvOR"), Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A | B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_OR   , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                       
   /* XOR              */
   /* A  <- Reg[rs1]   */,Label("riscvXOR"),Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A ^ B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_0, ALU_XOR  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J), "FETCH")
                                                                                           
   
   /* --- Control Transfer Instructions ------------- */
              
   // note: Reg[PC] is actually storing PC+4...
    
   /* J                */ 
   /* A  <- PC         */,Label("J"),       Signals(Cat(LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* A  <- A - 4      */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X,ALU_DEC_A_4,AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B  <- Sext(Imm25)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_J  , IEN_1, UBR_N),  "X") 
   /* PC <- A + B      */,                  Signals(Cat(LDIR_0, RS_PC , RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
    
   /* JAL              */ 
   /* A  <- PC         */,Label("JAL"),     Signals(Cat(LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* Reg[x1] <- A     */,                  Signals(Cat(LDIR_0, RS_RA , RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A,AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* A  <- A - 4      */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_1, LDB_X,ALU_DEC_A_4,AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B  <- Sext(Imm25)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_J  , IEN_1, UBR_N),  "X") 
   /* PC <- A + B      */,                  Signals(Cat(LDIR_0, RS_PC , RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
                               
   /* JALR             */ 
   /*                  */,Label("JALR_C")
   /*                  */,Label("JALR_R")
   /* A  <- PC         */,Label("JALR_J"),  Signals(Cat(LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* Reg[rd] <- A     */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A,AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* A  <- Reg[rs1]   */,                  Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_I  , IEN_1, UBR_N),  "X") 
   /* PC,A <- A + B    */,                  Signals(Cat(LDIR_0, RS_PC , RWR_1, REN_1, LDA_1, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
      
   /* AUIPC            */
   /* A  <- PC         */,Label("AUIPC"),   Signals(Cat(LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* A  <- A - 4      */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X,ALU_DEC_A_4,AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B  <- Imm-UType  */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_L  , IEN_1, UBR_N),  "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_X, LDB_X, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")


//   /* RDNPC            */ 
//   /* A  <- PC         */,Label("RDNPC"),   Signals(Cat(LDIR_0, RS_PC, RWR_0, REN_1, LDA_1, LDB_X, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
//   /* Reg[rd] <- A     */,                  Signals(Cat(LDIR_0, RS_RD, RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
//   /* note, RDNPC writes (PC+4) to Reg[rd]... */
       
   /* BEQ              */ 
   /* A <- Reg[rs1]    */,Label("BEQ"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X") 
   /* if zero?(A-B)    */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_SUB  , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
        
   /* BNE              */ 
   /* A <- Reg[rs1]    */,Label("BNE"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X") 
   /* if not zero?(A-B)*/,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_SUB  , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
         
   /* BLT              */ 
   /* A <- Reg[rs1]    */,Label("BLT"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X") 
   /* A <- (A < B)     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLT  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A,AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
        
   /* BLTU             */ 
   /* A <- Reg[rs1]    */,Label("BLTU"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X") 
   /* A <- (A < B)     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLTU , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A,AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
        
   /* BGE              */ 
   /* A <- Reg[rs1]    */,Label("BGE"),     Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X") 
   /* A <- (A < B)     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLT  , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A,AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X  , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
       
   /* BGEU             */ 
   /* A <- Reg[rs1]    */,Label("BGEU"),    Signals(Cat(LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X") 
   /* A <- (A < B)     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLTU , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A,AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/ 
   /* else             */,                  Signals(Cat(LDIR_0, RS_X  , RWR_X, REN_0, LDA_X, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */ 
       
   /* BZ-TAKEN        */ 
   /* note: PC register is actually 
      holding PC+4 (see 'FETCH2), so we have to
      dec4 to get the correct behavior. */
   /* A  <- PC        */,Label("BZ_TAKEN"), Signals(Cat(LDIR_0, RS_PC, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N), "X")
   /* A  <- A - 4     */,                   Signals(Cat(LDIR_0, RS_X , RWR_0, REN_0, LDA_1, LDB_X, ALU_DEC_A_4, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N), "X")
   /* B  <- SSH1(Imm) */,                   Signals(Cat(LDIR_0, RS_X , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_X, MEN_0, IS_BR, IEN_1, UBR_N), "X")
   /* PC <- A + B     */,                   Signals(Cat(LDIR_0, RS_PC, RWR_1, REN_1, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH   */



   /* --- Host/Target Interface Instructions -------- */
    
   /* MTPCR0          */ 
   /* A  <- Reg[rs2]  */,Label("MTPCR"),    Signals(Cat(LDIR_0, RS_RS2, RWR_0, REN_1, LDA_1, LDB_X, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N), "X")
   /* CP0[rs1] <- A   */,                   Signals(Cat(LDIR_0, RS_CP , RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N), "X")
   /* RD <- A         */,                   Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH    */
   
   /* MFPCR0          */ 
   /* A  <- CP0[rs2]  */,Label("MFPCR"),    Signals(Cat(LDIR_0, RS_CP , RWR_0, REN_1, LDA_1, LDB_X, ALU_X     , AEN_0, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A    */,                   Signals(Cat(LDIR_0, RS_RD , RWR_1, REN_1, LDA_0, LDB_X, ALU_COPY_A, AEN_1, LDMA_X, MWR_X, MEN_0, IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH    */
            
 ) 
}

}
 
