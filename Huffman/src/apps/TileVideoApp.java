package apps;

import io.BitSink;
import io.BitSource;
import io.InputStreamBitSource;
import io.InsufficientBitsLeftException;
import io.OutputStreamBitSink;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import models.Symbol;
import models.SymbolModel;
import models.Unsigned8BitModel;
import models.Unsigned8BitModel.Unsigned8BitSymbol;
import codec.ArithmeticDecoder;
import codec.ArithmeticEncoder;
import codec.SymbolDecoder;
import codec.SymbolEncoder;


public class TileVideoApp extends VideoApp {

	public static final int WIDTH = 800;
	public static final int HEIGHT = 450;
	public static final int NUM_FRAMES = 150;
	
	public static final int TILE_SIZE = 3;
	public static final int BUCKETS = 4;
	public static final int DICTIONARY_SIZE = 64;
	
	public static void main(String[] args) throws IOException, InsufficientBitsLeftException {
		String base = "bunny";
		String filename= base + ".450p.yuv";
		File file = new File(filename);
		int width = 800;
		int height = 450;
		int num_frames = 150;


		Unsigned8BitModel dictionaryModel = new Unsigned8BitModel();
		Unsigned8BitModel residualModel = new Unsigned8BitModel();
		
		InputStream training_values = new FileInputStream(file);
		
		//training_values.close();	
		int[] dictionary = generateTileDictonary(training_values, TILE_SIZE, BUCKETS, DICTIONARY_SIZE);
		
		training_values = new FileInputStream(file);
		
		int[][] current_frame = new int[WIDTH][HEIGHT];
		int[][][] video_dictionary_encoding = new int[NUM_FRAMES][(WIDTH-TILE_SIZE+1)/TILE_SIZE][(HEIGHT-TILE_SIZE+1)/TILE_SIZE];
		for (int f=0; f < NUM_FRAMES; f++) {
			current_frame = readFrame(training_values, WIDTH, HEIGHT);
			 int[][] dictionaryFrame = encodeFrameWithDictonary(current_frame, TILE_SIZE, BUCKETS, dictionary);
			 video_dictionary_encoding[f] = dictionaryFrame;
			 trainModelWithFrame(dictionaryModel, dictionaryFrame);
		}
		
		training_values = new FileInputStream(file);
		for (int f=0; f < NUM_FRAMES; f++) {
			current_frame = readFrame(training_values, WIDTH, HEIGHT);
			int[][] residualFrame = residualsFromEncodedFrame(current_frame, video_dictionary_encoding[f], TILE_SIZE, BUCKETS, dictionary);
			trainModelWithFrame(residualModel, residualFrame);
		}
		
		SymbolEncoder dictionaryEncoder = new ArithmeticEncoder(dictionaryModel);
		Symbol[] dictionarySymbols = new Unsigned8BitSymbol[256];
		for (int v=0; v<256; v++) {
			SymbolModel s = dictionaryModel.getByIndex(v);
			Symbol sym = s.getSymbol();
			dictionarySymbols[v] = sym;

			long prob = s.getProbability(dictionaryModel.getCountTotal());
			System.out.println("Symbol: " + sym + " probability: " + prob + "/" + dictionaryModel.getCountTotal());
		}			

		SymbolEncoder residualEncoder = new ArithmeticEncoder(residualModel);
		Symbol[] residualSymbols = new Unsigned8BitSymbol[256];
		for (int v=0; v<256; v++) {
			SymbolModel s = residualModel.getByIndex(v);
			Symbol sym = s.getSymbol();
			residualSymbols[v] = sym;

			long prob = s.getProbability(residualModel.getCountTotal());
			System.out.println("Symbol: " + sym + " probability: " + prob + "/" + residualModel.getCountTotal());
		}			
		
		//InputStream message = new FileInputStream(file);

		File out_file = new File(base + "-dictionary.dat");
		OutputStream out_stream = new FileOutputStream(out_file);
		BitSink bit_sink = new OutputStreamBitSink(out_stream);

		for (int f=0; f < num_frames; f++) {
			System.out.println("Encoding frame difference " + f);
			int[][] frame_dictionary = video_dictionary_encoding[f];
			//int[][] diff_frame = frameDifference(prior_frame, current_frame);
			encodeFrameDifference(frame_dictionary, dictionaryEncoder, bit_sink, dictionarySymbols);
		}

		dictionaryEncoder.close(bit_sink);
		out_stream.close();

		
		InputStream message = new FileInputStream(file);

		out_file = new File(base + "-residuals.dat");
		out_stream = new FileOutputStream(out_file);
		bit_sink = new OutputStreamBitSink(out_stream);

		current_frame = new int[width][height];

		for (int f=0; f < num_frames; f++) {
			System.out.println("Encoding frame difference " + f);
			current_frame = readFrame(training_values, WIDTH, HEIGHT);
			int[][] residualFrame = residualsFromEncodedFrame(current_frame, video_dictionary_encoding[f], TILE_SIZE, BUCKETS, dictionary);
			encodeFrameDifference(residualFrame, residualEncoder, bit_sink, residualSymbols);
		}
		
		message.close();
		residualEncoder.close(bit_sink);
		out_stream.close();
	}
		
		/*BitSource bit_source = new InputStreamBitSource(new FileInputStream(out_file));
		OutputStream decoded_file = new FileOutputStream(new File("/Users/kmp/tmp/" + base + "-decoded.dat"));

		//		SymbolDecoder decoder = new HuffmanDecoder(encoder.getCodeMap());
		SymbolDecoder decoder = new ArithmeticDecoder(model);

		current_frame = new int[width][height];

		for (int f=0; f<num_frames; f++) {
			System.out.println("Decoding frame " + f);
			int[][] prior_frame = current_frame;
			int[][] diff_frame = decodeFrame(decoder, bit_source, width, height);
			current_frame = reconstructFrame(prior_frame, diff_frame);
			outputFrame(current_frame, decoded_file);
		}

		decoded_file.close();
		
	}
	*/
	
	
	
	
	
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
	
		final class Pair implements Comparable<Pair>{
			int idx;
			int count;
			public Pair(int idx) {
				this.idx = idx;
			}
			public int compareTo(Pair pair2) {
				if(this.count > pair2.count) return -1;//invert the sorting so biggest will be first in the array.
				else if(pair2.count > this.count) return 1;
				else if(this.idx < pair2.idx) return -1;
				else return 1;
			}
			
		}
	
		
		Pair[] tileCount = new Pair[(int) Math.pow(buckets,tileSize * tileSize)]; 
		for (int i=0; i<tileCount.length;i++) tileCount[i] = new Pair(i);
		for (int f=0; f < NUM_FRAMES; f++) {
			current_frame = readFrame(videoStream, WIDTH, HEIGHT);
			for (int y=0; y<HEIGHT-tileSize+1; y+= tileSize) {
				for (int x=0; x<WIDTH-tileSize+1; x+= tileSize) {
					int tileNum = tileNum(current_frame, x, y, tileSize, buckets);
					tileCount[tileNum].count++;
					//if(tileNum==65536){
					//	System.out.println("Hello World");
					//}
				}
			}
			System.out.println("Frame i: " + f);
		}
		
		java.util.Arrays.sort(tileCount);

		
		int[] dictonary = new int[dictonarySize];
		for(int i=0; i < dictonarySize; i++) dictonary[i] = -1;
		
		for(int i=0; i < dictonarySize; i++){
			Pair val_i = tileCount[i];
			dictonary[i] = val_i.idx;
		}
		return dictonary;
	}
	
	public static int[][] encodeFrameWithDictonary(int[][] frame, int tileSize, int buckets, int[] dictonary){
		int tileWidth = (WIDTH-tileSize+1) / tileSize;
		int tileHeight = (HEIGHT-tileSize+1) / tileSize;
		int[][] tileFrame = new int[tileWidth][tileHeight];
		
		for (int y=0; y<tileHeight; y++) {
			for (int x=0; x<tileWidth; x++) {
				int bestTile = TileVideoApp.bestTileFromDictonary(frame, x, y, tileSize, buckets, dictonary);
				tileFrame[x][y] = bestTile;
			}
		}
		return tileFrame;
	}
	
	public static int[][] residualsFromEncodedFrame(int[][] frame, int[][] tileFrame, int tileSize, int buckets, int[] dictonary){
		int[][] residuals = new int[WIDTH][];
		for(int i=0; i < HEIGHT; i++) residuals[i] = (int[]) frame[i].clone();
		for (int y=0; y<HEIGHT-tileSize+1; y+= tileSize) {
			for (int x=0; x<WIDTH-tileSize+1; x+= tileSize) {
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
