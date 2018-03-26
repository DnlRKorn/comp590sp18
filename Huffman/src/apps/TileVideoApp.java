package apps;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public class TileVideoApp extends VideoApp {

	public static final int WIDTH = 800;
	public static final int HEIGHT = 450;
	public static final int NUM_FRAMES = 150;
	
	/**
	 * Given an area of a frame (a tile), specified by a frame, an x y coordinate pair, and a size. 
	 * Returns the frame number that will minimize total differences.
	 *
	 * @param frame Image of the current frame. 
	 * @param tile_x X coordinate of the tile relative to the frame.
	 * @param tile_y Y coordinate of the tile relative to the frame.
	 * @param tileSize Size of the tile. If this is 1, it will do bucketing on a pixel level.
	 * @param buckets Number of buckets we want our model to have for each tile.
	 * 
	 * @return tileNum Number that represents the area of our frame in a single digit.
	 */
	public static int tileNum(int[][] frame, int tile_x, int tile_y, int tileSize, int buckets){
		int bucketSize = 256/buckets;
		int tileNum = 0;
		int frameVal;
		for (int y=tile_y; y<tileSize + tile_y; y++) {
			for (int x=tile_x; x<tile_x+tileSize; x++) {
				//Want to get the buckets which start at 0 and spread out [-bucketSize / 2 , bucketSize/2]
				//to be initialize at 0. 
				frameVal = (frame[x][y] + bucketSize/2) % 256;
				int bucketNum = 0;
				while(frameVal >= bucketSize){ //[0 to bucketSize) counts as bucket.
					bucketNum++;
					frameVal -= bucketSize;
				}
				tileNum *= buckets;//If the number of buckets is a power of 2, this will shift the bits of tileNum.
				tileNum += bucketNum;//Append the bits of the tile count on the end
				}
		}
		return tileNum;
	}
	
	/**
	 * Given a tile number, the length and height of the tile, and the number of buckets we have, 
	 * will reconstruct the tile and return an array representation.
	 * 
	 * @param tileNum Number that represents tile we wish to reconstruct. Typically shifted bits.
	 * @param buckets Number of buckets we want our model to have for each tile. Will start at 0 and have a bucket 
	 * every 256/buckets.
	 * 
	 * @return tile A double array which is tileSize x tileSize. A reconstruction of the tile we wish to encode
	 * our video with.
	 */
	public static int[][] tileNumToTile(int tileNum, int tileSize, int buckets){
		int bucketSize = 256/buckets;
		int tileTmp = tileNum;
		int[][] tile = new int[tileSize][tileSize];
		for (int y=tileSize -1; y>= 0; y--) {
			for (int x=tileSize - 1; x>= 0; x--) {
				int tmp = tileTmp % buckets;
				tile[x][y] = tmp * bucketSize;
				tileTmp /= buckets;
			}
		}
		return tile;
	}
	
	
	/**
	 * Given a fixed dictionary of tiles, will find the best possible tile for an area of a frame.
	 * Best is determined by smallest absolute difference.
	 *
	 * @param frame Image of the current frame. 
	 * @param tile_x X coordinate of the tile relative to the frame.
	 * @param tile_y Y coordinate of the tile relative to the frame.
	 * @param tileSize Size of the tile. If this is 1, it will do bucketing on a pixel level.
	 * @param buckets Number of buckets we want our model to have for each tile.
	 * @param dictionary Array of tile numbers which represent a dictionary of possible tiles we can encode.
	 * 
	 * @return bestDic Index of the tile in the dictionary array which will minimize absolute difference for our tile within the
	 * frame during encoding.
	 */
	private static int bestTileFromDictonary(int[][] frame, int tile_x, int tile_y, int tileSize, int buckets, int[] dictionary){
		int bestDic = -1;
		int bestDiff = Integer.MAX_VALUE;
		for(int i=0; i<dictionary.length;i++){
			int[][] dictTile = tileNumToTile(dictionary[i], tileSize, buckets);
			int absDiff = 0;
			for (int y=0; y<tileSize; y++) {
				for (int x=0; x<tileSize; x++) {
					absDiff += Math.abs(frame[tile_x+x][tile_y+y] - dictTile[x][y]);
				}
			}
			if(absDiff < bestDiff){
				bestDiff = absDiff;
				bestDic = i;
			}
		}
		return bestDic;
	}
	
	public static int[] generateTileDictonary(InputStream videoStream, int tileSize, int buckets, int dictonarySize) throws IOException{
		int[][] current_frame = new int[WIDTH][HEIGHT];
	
		int[] tileCount = new int[buckets ^ tileSize]; 
		
		for (int f=0; f < NUM_FRAMES; f++) {
			current_frame = readFrame(videoStream, WIDTH, HEIGHT);
			for (int y=0; y<HEIGHT-tileSize+1; y+= tileSize) {
				for (int x=0; x<WIDTH-tileSize+1; x+= tileSize) {
					int tileNum = tileNum(current_frame, x, y, tileSize, buckets);
					tileCount[tileNum]++;
				}
			}
		}

		int[] sortedTileCount = (int[]) tileCount.clone();
		java.util.Arrays.sort(sortedTileCount);
		int[] dictonary = new int[dictonarySize];
		for(int i=0; i < dictonarySize; i++) dictonary[i] = -1;
		for(int i=0; i < dictonarySize; i++){
			int val_i = sortedTileCount[i];
			innerLoop: for(int j=0; j<tileCount.length;i++){
				ifStatement: if(val_i == tileCount[j]){
					for(int k = 0; k < i; k++){
						if(dictonary[k] == j) break ifStatement;//Go to next element with value in tileCount.
					}
					dictonary[i] = j;
					break innerLoop;
				}
			}
		}
		return dictonary;
	}
	
	public static int[][] encodeFrameWithDictonary(int[][] frame, int tileSize, int buckets, int[] dictonary){
		int tileWidth = WIDTH / tileSize;
		int tileHeight = HEIGHT / tileSize;
		int[][] tileFrame = new int[tileWidth][tileHeight];
		
		for (int y=0; y<HEIGHT-tileSize+1; y+= tileSize) {
			for (int x=0; x<WIDTH-tileSize+1; x+= tileSize) {
				int bestTile = TileVideoApp.bestTileFromDictonary(frame, x, y, tileSize, buckets, dictonary);
				tileFrame[x][y] = bestTile;
			}
		}
		return tileFrame;
	}
	
	public static int[][] residualsFromEncodedFrame(int[][] frame, int[][] tileFrame, int tileSize, int buckets, int[] dictonary){
		int[][] residuals = new int[WIDTH][];
		for(int i=0; i < HEIGHT; i++) residuals[i] = (int[]) frame[i].clone();
		for (int y=0; y<HEIGHT; y+= tileSize) {
			for (int x=0; x<WIDTH; x+= tileSize) {
				int dictonaryIdx = tileFrame[x][y];
				int[][] tile = tileNumToTile(dictonary[dictonaryIdx], tileSize, buckets);
				for(int j=0;j<tileSize;j++){
					for(int i=0;i<tileSize;i++){
						residuals[x*tileSize+i][y*tileSize+j] -= tile[i][j];
					}
				}
			}
		}
		return residuals;
	}
	
}
