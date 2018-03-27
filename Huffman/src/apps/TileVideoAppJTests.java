package apps;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import org.junit.Test;
import org.mockito.Mockito;

public class TileVideoAppJTests {

	@Test
	public void test() {
		assert(true);
	}

	@Test
	public void tileNumTest(){
	//tileNum(int[][] frame, int tile_x, int tile_y, int tileSize, int buckets)
		int buckets = 4;
		int tileSize = 3;
		int[][] frame = new int[3][3];
		for(int i = 0; i<3;i++)
			for(int j=0; j<3;j++)
				frame[i][j] = 128;
	
		int tileNum = TileVideoApp.tileNum(frame,0,0,tileSize,buckets);
		int corrTileNum = 0;
		for(int i=0; i<9;i++){
			corrTileNum *= buckets;
			corrTileNum += 2;
		}
		assertEquals(tileNum, corrTileNum);
		
		frame = new int[1024][1024];
		for(int i = 500; i<503; i++){
			for(int j = 500; j<503; j++){
				//frame[i][j] = (i*j)%256;
				frame[i][j] = 196;
			}
		}
		tileNum = TileVideoApp.tileNum(frame, 500, 500, tileSize, buckets);
		corrTileNum = 0;
		for(int i=0; i<9;i++){
			corrTileNum *= buckets;
			corrTileNum += 3;
		}
		assertEquals(tileNum, corrTileNum);
	}
	
	
	@Test
	public void tileNumToTileTest(){
		int tileSize = 3;
		int buckets = 4;
		int bucketSize = 256/buckets;
		int[][] tile = TileVideoApp.tileNumToTile(0, tileSize, buckets);
		for(int i = 0; i < 3; i++){
			for(int j=0; j<3; j++){
				assertEquals(tile[j][i], 0);
			}
		}
		int tileNum = 0;
		for(int i=0;i<9;i++){
			tileNum *= 4;
			tileNum += 2;
		}
		tile = TileVideoApp.tileNumToTile(tileNum, tileSize, buckets);
		int targetPixelForDict = 2*bucketSize; //128 = 2 * bucketSize
		for(int i = 0; i < 3; i++){
			for(int j=0; j<3; j++){
				assertEquals(tile[j][i], 128);
			}
		}
		
	}
	
	
	@Test
	public void generateTileDictonaryTest() throws IOException{
		
		String filename= "test.bin";
		File file = new File(filename);
	    //BufferedWriter writer = new BufferedWriter(new FileWriter(file));
	    //for(int i = 0; i<800*450*150;i++) writer.write(0);
	    //writer.close();
		InputStream test_vals = new FileInputStream(file);
		int tileSize = 3;
		int buckets = 4;
		int dictSize = 1;
		//int[] dict = TileVideoApp.generateTileDictonary(test_vals, tileSize, buckets, dictSize);
		//test_vals.close();
		//assertEquals(dict.length, 1);
		//assertEquals(dict[0], 0);
		//file.delete();
		//file = new File(filename);
	    //writer = new BufferedWriter(new FileWriter(file));
	    //writer.write(128); //write a single frame with a single pixel in bucket 2.
	    //for(int i = 1; i<800*450*150;i++) writer.write(0);
	    //writer.close();
		dictSize = 2;
		//test_vals = new FileInputStream(file);
		int[] dict = TileVideoApp.generateTileDictonary(test_vals, tileSize, buckets, dictSize);
		assertEquals(dict.length, 2);
		assertEquals(0, dict[0]);
		int target = (int) (1 * Math.pow(4,8));
		assertEquals(dict[1], target);
		//file.delete();
	}
}
