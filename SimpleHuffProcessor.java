/*  Student information for assignment:
 *
 *  On <MY|OUR> honor, <NAME1> and <NAME2), this programming assignment is <MY|OUR> own work
 *  and <I|WE> have not provided this code to any other student.
 *
 *  Number of slip days used:
 *
 *  Student 1 (Student whose Canvas account is being used)
 *  UTEID:
 *  email address:
 *  Grader name:
 *
 *  Student 2
 *  UTEID:
 *  email address:
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class SimpleHuffProcessor implements IHuffProcessor {

    private IHuffViewer myViewer;
    private int header;
    private TreeNode huffTreeRoot;
    private int[] freqs;
    private Map<Integer, String> huffCodes;
    private int writtenBitNum;
    private int treeHeaderSize;

    /**
     * Preprocess data so that compression is possible ---
     * count characters/create tree/store state so that
     * a subsequent call to compress will work. The InputStream
     * is <em>not</em> a BitInputStream, so wrap it int one as needed.
     * 
     * @param in           is the stream which could be subsequently compressed
     * @param headerFormat a constant from IHuffProcessor that determines what kind
     *                     of
     *                     header to use, standard count format, standard tree
     *                     format, or
     *                     possibly some format added in the future.
     * @return number of bits saved by compression or some other measure
     *         Note, to determine the number of
     *         bits saved, the number of bits written includes
     *         ALL bits that will be written including the
     *         magic number, the header format number, the header to
     *         reproduce the tree, AND the actual data.
     * @throws IOException if an error occurs while reading from the input file.
     */
    public int preprocessCompress(InputStream in, int headerFormat) throws IOException {
        PriorityQueue<TreeNode> huffTreeQ = new PriorityQueue<>();
        this.writtenBitNum = 0;
        this.header = headerFormat;
        BitInputStream inStream = new BitInputStream(in);
        HashMap<Integer, Integer> freqMap = createFreqMap(inStream);

        int uncompSize = 0;
        int compSize = 0;

        // Creates priority queue with each char
        for (Integer asciiVal : freqMap.keySet()) {
            int charFreq = freqMap.get(asciiVal);
            TreeNode charNode = new TreeNode(asciiVal, charFreq);

            huffTreeQ.enqueue(charNode);
            uncompSize += charFreq * BITS_PER_WORD;

        }

        // Makes huffTree
        while (huffTreeQ.size() > 1) {
            // Assume dequeue fetches and removes the smallest element
            TreeNode left = huffTreeQ.dequeue();
            // Fetch the next smallest element
            TreeNode right = huffTreeQ.dequeue();

            TreeNode combined = new TreeNode(left, left.getFrequency() + right.getFrequency(), right);
            // Add the combined node back to the queue
            huffTreeQ.enqueue(combined);
        }

        // The last remaining node is the root of the Huffman tree
        TreeNode huffmanTreeRoot = huffTreeQ.dequeue();
        this.huffTreeRoot = huffmanTreeRoot;

        Map<Integer, String> huffCodes = generateHuffCode(this.huffTreeRoot, "");
        this.huffCodes = huffCodes;

        for (Integer i : freqMap.keySet()) {
            int asciiVal = i;
            int charFreq = freqMap.get(i);
            String huffCode = huffCodes.get(asciiVal);
            compSize += huffCode.length() * charFreq;
        }

        return uncompSize - compSize;
    }

    private HashMap<Integer, Integer> createFreqMap(BitInputStream inStream) throws IOException {
        // HashMap<String, Integer> freqMap = new HashMap<>();
        HashMap<Integer, Integer> freqMap = new HashMap<>();
        this.freqs = new int[ALPH_SIZE];

        int curVal = inStream.read();
        while (curVal != -1) {
            // String letter = Character.toString((char) curVal);
            freqMap.put(curVal, freqMap.getOrDefault(curVal, 0) + 1);
            freqs[curVal]++;
            curVal = inStream.read();
        }

        freqMap.put(PSEUDO_EOF, 1);

        return freqMap;
    }

    private Map<Integer, String> generateHuffCode(TreeNode curNode, String curPath) {
        Map<Integer, String> huffMap = new HashMap<>();
        if (curNode.isLeaf()) {
            // If it's a leaf node, put the ASCII value and its path in the map
            huffMap.put(curNode.getValue(), curPath);
        } else {
            // Recursively generate codes for left subtree, appending "0" to the path
            if (curNode.getLeft() != null) {
                huffMap.putAll(generateHuffCode(curNode.getLeft(), curPath + "0"));
            }
            // Recursively generate codes for right subtree, appending "1" to the path
            if (curNode.getRight() != null) {
                huffMap.putAll(generateHuffCode(curNode.getRight(), curPath + "1"));
            }
        }
        return huffMap;
    }

    /**
     * Compresses input to output, where the same InputStream has
     * previously been pre-processed via <code>preprocessCompress</code>
     * storing state used by this call.
     * <br>
     * pre: <code>preprocessCompress</code> must be called before this method
     * 
     * @param in    is the stream being compressed (NOT a BitInputStream)
     * @param out   is bound to a file/stream to which bits are written
     *              for the compressed file (not a BitOutputStream)
     * @param force if this is true create the output file even if it is larger than
     *              the input file.
     *              If this is false do not create the output file if it is larger
     *              than the input file.
     * @return the number of bits written.
     * @throws IOException if an error occurs while reading from the input file or
     *                     writing to the output file.
     */
    public int compress(InputStream in, OutputStream out, boolean force) throws IOException {

        // for(Integer i : huffCodes.keySet()){
        // System.out.println(i + " : " + huffCodes.get(i));
        // }

        BitInputStream inStream = new BitInputStream(in);
        BitOutputStream outStream = new BitOutputStream(out);

        outStream.writeBits(BITS_PER_INT, MAGIC_NUMBER);
        writtenBitNum += BITS_PER_INT;

        if (this.header == STORE_COUNTS) {
            outStream.writeBits(BITS_PER_INT, STORE_COUNTS);
            writtenBitNum += BITS_PER_INT;

            for (int k = 0; k < ALPH_SIZE; k++) {
                outStream.writeBits(BITS_PER_INT, this.freqs[k]);
                writtenBitNum += BITS_PER_INT;
            }
        } else if (this.header == STORE_TREE) {
            outStream.writeBits(BITS_PER_INT, STORE_TREE);
            writtenBitNum += BITS_PER_INT;
            // Flatten tree
            writeTreeForSTF(this.huffTreeRoot, outStream);
        }

        int curVal = inStream.read();
        while (curVal != -1) {

            String huffStr = huffCodes.get(curVal);

            int bitNum = huffStr.length();
            int huffVal = Integer.parseInt(huffStr, 2);

            // System.out.println(Integer.toBinaryString(huffVal));
            outStream.writeBits(bitNum, huffVal);
            writtenBitNum += bitNum;

            curVal = inStream.read();

        }

        outStream.close();
        inStream.close();

        myViewer.showMessage("Wrote: " + writtenBitNum);
        return writtenBitNum;
    }

    private void writeTreeForSTF(TreeNode curNode, BitOutputStream outStream) {
        if (curNode == null) {
            return;
        }

        if (curNode.isLeaf()) {
            outStream.writeBits(1, 1);
            outStream.writeBits(BITS_PER_WORD + 1, curNode.getValue());
            writtenBitNum += BITS_PER_WORD + 2;

        } else {
            outStream.writeBits(1, 0);
            writtenBitNum += 1;
            writeTreeForSTF(curNode.getLeft(), outStream);
            writeTreeForSTF(curNode.getRight(), outStream);
        }
    }

    /**
     * Uncompress a previously compressed stream in, writing the
     * uncompressed bits/data to out.
     * 
     * @param in  is the previously compressed data (not a BitInputStream)
     * @param out is the uncompressed file/stream
     * @return the number of bits written to the uncompressed file/stream
     * @throws IOException if an error occurs while reading from the input file or
     *                     writing to the output file.
     */
    public int uncompress(InputStream in, OutputStream out) throws IOException {
        BitInputStream inStream = new BitInputStream(in);
        BitOutputStream outStream = new BitOutputStream(out);

        // Read and validate the magic number
        int magic = inStream.readBits(BITS_PER_INT);
        if (magic != MAGIC_NUMBER) {
            inStream.close();
            outStream.close();
            throw new IOException("Invalid magic number, file may not be compressed with this format.");
        }

        // Read the header type
        int headerType = inStream.readBits(BITS_PER_INT);
        if (headerType != STORE_COUNTS) {
            inStream.close();
            outStream.close();
            throw new IOException("Unsupported header type for this decompression method.");
        }

        // Rebuild the frequency table from the header
        int[] freqsRebuilt = new int[ALPH_SIZE];
        for (int k = 0; k < ALPH_SIZE; k++) {
            freqsRebuilt[k] = inStream.readBits(BITS_PER_INT);
        }

        // Construct the Huffman tree from the frequency table
        PriorityQueue<TreeNode> huffTreeQ = new PriorityQueue<>();
        for (int i = 0; i < ALPH_SIZE; i++) {
            if (freqsRebuilt[i] > 0) {
                huffTreeQ.enqueue(new TreeNode(i, freqsRebuilt[i]));
            }
        }
        while (huffTreeQ.size() > 1) {
            TreeNode left = huffTreeQ.dequeue();
            TreeNode right = huffTreeQ.dequeue();
            TreeNode combined = new TreeNode(left, -1, right);
            huffTreeQ.enqueue(combined);
        }
        TreeNode root = huffTreeQ.dequeue();

        // Decode the data using the Huffman tree
        int totalBitsWritten = 0;
        boolean endOfFile = false;
        while (!endOfFile) {
            TreeNode currentNode = root;
            while (!currentNode.isLeaf()) {
                int bit = inStream.readBits(1);
                if (bit == -1) {
                    if (currentNode == root) {
                        endOfFile = true; // Handle correctly when end of file is reached naturally
                        break;
                    } else {
                        inStream.close();
                        outStream.close();
                        throw new IOException("Unexpected end of input stream while decoding.");
                    }
                }
                currentNode = (bit == 0) ? currentNode.getLeft() : currentNode.getRight();
            }

            if (!endOfFile && currentNode.getValue() == PSEUDO_EOF) {
                endOfFile = true; // Stop when PSEUDO_EOF is encountered
            } else if (!endOfFile) {
                outStream.write(currentNode.getValue());
                totalBitsWritten += BITS_PER_WORD; // Increment counter by the number of bits in a word
            }
        }

        outStream.flush();
        outStream.close();
        inStream.close();

        return totalBitsWritten; // Return the number of bits written to the output
    }

    public void setViewer(IHuffViewer viewer) {
        myViewer = viewer;
    }

    private void showString(String s) {
        if (myViewer != null) {
            myViewer.update(s);
        }
    }
}
