import java.io.*;

/**
 * This class is for ROM data and tables that will be used by our Othello Chess program.
 * @author Nicholas Ver Hoeve
 */
public class Rom {
	/**
	 * This large table contains the effects of placing a piece in any of 8 positions in
	 * a row (8 squares) of an othello game. There are 256 arrangments of friendly pieces
	 * and 256 arrangements of black pieces in this table, so the table is 
	 * 8*256*256 = 524288 Bytes = 512kB
	 * 
	 * The desired input index of the table is computed by this formula:
	 * index = friendlyRow | (enemyRow << 8) | (newPosition << 16)];
	 */
	public static byte[] ROWLOOKUP = loadTable();
	
	static private byte[] loadTable() {
		byte[] array = new byte[8*256*256];
		try {
			File file = new File("RowLookup.dat");
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			in.read(array);
			in.close();
		} catch (IOException e) {
			System.err.println("WARNING: could not load table");
		}
		return array;
	}
	
	/**
	 * This lookup table can map the board index (0-63) to the bitboard that has set bits in
	 * every direction from the indexed point on the board. This is left/right, up/down, and
	 * diagonal.
	 * 
	 * Another way to think of this table is if in Chess, given a queen on an empty board, 
	 * whose position is given by the input-index, the output from the table would be the 
	 * bitboard of all legal positions the queen can move to.
	 */
	public static long[] ALLDIRECTIONLOOKUP = new long[] {
		0x81412111090503FEL, 0x02824222120A07FDL, 0x0404844424150EFBL, 0x08080888492A1CF7L, 
		0x10101011925438EFL, 0x2020212224A870DFL, 0x404142444850E0BFL, 0x8182848890A0C07FL, 
		0x412111090503FE03L, 0x824222120A07FD07L, 0x04844424150EFB0EL, 0x080888492A1CF71CL, 
		0x101011925438EF38L, 0x20212224A870DF70L, 0x4142444850E0BFE0L, 0x82848890A0C07FC0L, 
		0x2111090503FE0305L, 0x4222120A07FD070AL, 0x844424150EFB0E15L, 0x0888492A1CF71C2AL, 
		0x1011925438EF3854L, 0x212224A870DF70A8L, 0x42444850E0BFE050L, 0x848890A0C07FC0A0L, 
		0x11090503FE030509L, 0x22120A07FD070A12L, 0x4424150EFB0E1524L, 0x88492A1CF71C2A49L, 
		0x11925438EF385492L, 0x2224A870DF70A824L, 0x444850E0BFE05048L, 0x8890A0C07FC0A090L, 
		0x090503FE03050911L, 0x120A07FD070A1222L, 0x24150EFB0E152444L, 0x492A1CF71C2A4988L, 
		0x925438EF38549211L, 0x24A870DF70A82422L, 0x4850E0BFE0504844L, 0x90A0C07FC0A09088L, 
		0x0503FE0305091121L, 0x0A07FD070A122242L, 0x150EFB0E15244484L, 0x2A1CF71C2A498808L, 
		0x5438EF3854921110L, 0xA870DF70A8242221L, 0x50E0BFE050484442L, 0xA0C07FC0A0908884L, 
		0x03FE030509112141L, 0x07FD070A12224282L, 0x0EFB0E1524448404L, 0x1CF71C2A49880808L, 
		0x38EF385492111010L, 0x70DF70A824222120L, 0xE0BFE05048444241L, 0xC07FC0A090888482L, 
		0xFE03050911214181L, 0xFD070A1222428202L, 0xFB0E152444840404L, 0xF71C2A4988080808L, 
		0xEF38549211101010L, 0xDF70A82422212020L, 0xBFE0504844424140L, 0x7FC0A09088848281L
	};
}
