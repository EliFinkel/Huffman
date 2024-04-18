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
    private int headerTreeSize;

    private int uncompSize;
    private int compSize;

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
        this.compSize = 0;
        this.uncompSize = 0;
        // Create empty priority queue
        PriorityQueue<TreeNode> huffTreeQ = new PriorityQueue<>();
        this.header = headerFormat;
        BitInputStream inStream = new BitInputStream(in);
        // Map to store ascii value of char and its freqs
        HashMap<Integer, Integer> freqMap = createFreqMap(inStream);

        // Creates and adds a TreeNode for each char in freqMap
        for (Integer asciiVal : freqMap.keySet()) {
            int charFreq = freqMap.get(asciiVal);
            TreeNode charNode = new TreeNode(asciiVal, charFreq);
            huffTreeQ.enqueue(charNode);
            uncompSize += charFreq * BITS_PER_WORD; // Calculate uncompressed size
        }

        // Add a TreeNode for PSEUDO_EOF
        huffTreeQ.enqueue(new TreeNode(PSEUDO_EOF, 1));

        // Build Huffman tree
        while (huffTreeQ.size() > 1) {
            TreeNode left = huffTreeQ.dequeue();
            TreeNode right = huffTreeQ.dequeue();
            TreeNode combined = new TreeNode(left, left.getFrequency() + right.getFrequency(), right);
            huffTreeQ.enqueue(combined);
        }

        // The remaining node is the root of the Huffman Tree
        TreeNode huffmanTreeRoot = huffTreeQ.dequeue();
        this.huffTreeRoot = huffmanTreeRoot;

        // Generate Huffman codes from the tree root
        Map<Integer, String> huffCodes = generateHuffCode(this.huffTreeRoot, "");
        // for (Integer asciiVal : huffCodes.keySet()) {
        // System.out.println(asciiVal + " --> " + huffCodes.get(asciiVal));
        // }
        this.huffCodes = huffCodes;

        for (Integer asciiVal : huffCodes.keySet()) {
            int codeLength = huffCodes.get(asciiVal).length();
            if (asciiVal == PSEUDO_EOF) {
                compSize += codeLength;
            } else {
                int codeFreq = freqMap.get(asciiVal);
                compSize += codeLength * codeFreq;
            }
        }

        compSize += BITS_PER_INT;
        compSize += BITS_PER_INT;

        if (headerFormat == STORE_COUNTS) {
            compSize += ALPH_SIZE * BITS_PER_INT;

        } else if (headerFormat == STORE_TREE) {
            updateHeaderInfo(huffmanTreeRoot);
            compSize += BITS_PER_INT;
        }

        return uncompSize - compSize; // Calculate and return the number of bits saved
    }

    private void updateHeaderInfo(TreeNode curNode) {
        // Return if we fell out of the tree
        if (curNode == null) {
            return;
        }
        if (curNode.isLeaf()) {
            this.compSize += BITS_PER_WORD + 2;

        } else {
            // Write 0 if internal node

            this.compSize += 1;
            // Recurse both left and right subtrees
            updateHeaderInfo(curNode.getLeft());
            updateHeaderInfo(curNode.getRight());
        }
    }

    private HashMap<Integer, Integer> createFreqMap(BitInputStream inStream) throws IOException {
        // HashMap to store ascii values and freqs
        HashMap<Integer, Integer> freqMap = new HashMap<>();
        // Map of char freqs to be sent when STORE_COUNTS
        this.freqs = new int[ALPH_SIZE];
        // Read everyvalue and add or update its frequency in the map
        int curVal = inStream.read();
        while (curVal != -1) {
            if (curVal == 250) {
                // System.out.println((char)curVal);
            }
            // String letter = Character.toString((char) curVal);
            freqMap.put(curVal, freqMap.getOrDefault(curVal, 0) + 1);

            freqs[curVal]++;
            curVal = inStream.read();
        }

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
        BitInputStream inStream = new BitInputStream(in);
        BitOutputStream outStream = new BitOutputStream(out);

        int compressedSize = this.compSize;
        int originalSize = in.available() * BITS_PER_WORD;

        // If the compressed file will be larger than the old file and we dont want to
        // force it return 0
        if (!force && compressedSize >= originalSize) {
            inStream.close();
            outStream.close();
            return 0;
        }

        this.writtenBitNum = 0;

        // Write out the magic number
        outStream.writeBits(BITS_PER_INT, MAGIC_NUMBER);
        this.writtenBitNum += BITS_PER_INT;

        if (this.header == STORE_COUNTS) {

            // Write out the header type
            outStream.writeBits(BITS_PER_INT, STORE_COUNTS);
            this.writtenBitNum += BITS_PER_INT;

            // Write out the freq at each index in freqs
            for (int k = 0; k < ALPH_SIZE; k++) {
                outStream.writeBits(BITS_PER_INT, this.freqs[k]);
                this.writtenBitNum += BITS_PER_INT;
            }
        } else if (this.header == STORE_TREE) {

            // Header type
            outStream.writeBits(BITS_PER_INT, STORE_TREE);
            this.writtenBitNum += BITS_PER_INT;
            // To handle tree size before flattened tree
            this.writtenBitNum += BITS_PER_INT;
            // MUST ACTUALLY WRITE OUT THE SIZE
            int[] sizeCount = { 0 };
            int treeSize = calcTreeSize(this.huffTreeRoot, sizeCount);
            outStream.writeBits(BITS_PER_INT, treeSize);
            // Write out flattend BST
            writeTreeForSTF(this.huffTreeRoot, outStream);
        }

        // Write out data coded with the huffman codes
        int curVal = inStream.read();
        while (curVal != -1) {
            String huffStr = huffCodes.get(curVal);
            int bitNum = huffStr.length();

            int huffVal = Integer.parseInt(huffStr, 2);

            outStream.writeBits(bitNum, huffVal);
            this.writtenBitNum += bitNum;

            curVal = inStream.read();
        }

        // Write PSUEDO_EOF
        String eofStr = huffCodes.get(PSEUDO_EOF);
        int eofBitNum = eofStr.length();
        int eofVal = Integer.parseInt(eofStr, 2);

        outStream.writeBits(eofBitNum, eofVal);
        this.writtenBitNum += eofBitNum;

        myViewer.showMessage("Wrote: " + this.writtenBitNum);
        outStream.close();
        inStream.close();
        return this.writtenBitNum;
    }

    private int calcTreeSize(TreeNode curNode, int[] size) {
        if(curNode == null){
            return -1;
        }
        if(curNode.isLeaf()){
            size[0] += 1;
            size[0] += BITS_PER_INT;
        } else{
            size[0] += 1;
            calcTreeSize(curNode.getLeft(), size);
            calcTreeSize(curNode.getRight(), size);
        }
        return size[0];
    }

    private void writeTreeForSTF(TreeNode curNode, BitOutputStream outStream) {
        // Return if we fell out of the tree
        if (curNode == null) {
            return;
        }
        if (curNode.isLeaf()) {
            // Write 1 if the node is leaf and then its ascii value of the char it stores
            outStream.writeBits(1, 1);
            outStream.writeBits(BITS_PER_WORD + 1, curNode.getValue());
            this.writtenBitNum += BITS_PER_WORD + 2;

        } else {
            // Write 0 if internal node
            outStream.writeBits(1, 0);
            this.writtenBitNum += 1;

            // Recurse both left and right subtrees
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

        int totalBitsWritten = 0;

        // Read the magic number to validate the file
        int magic = inStream.readBits(BITS_PER_INT);
        if (magic != MAGIC_NUMBER) {
            inStream.close();
            outStream.close();
            throw new IOException("Invalid magic number, file may not be compressed with this format.");
        }

        // Read the header type
        int headerType = inStream.readBits(BITS_PER_INT);

        // Create a arbitrary node to store root
        TreeNode root = new TreeNode(0, 0);

        if (headerType == STORE_COUNTS) {
            System.out.println("COUNT");
            // Rebuild the frequency array from the header if STORE_COUNTS
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

            // Add a node for PSUEDO_EOF
            huffTreeQ.enqueue(new TreeNode(PSEUDO_EOF, 1));

            // Build huffman tree
            while (huffTreeQ.size() > 1) {
                TreeNode left = huffTreeQ.dequeue();
                TreeNode right = huffTreeQ.dequeue();
                TreeNode combined = new TreeNode(left, left.getFrequency() + right.getFrequency(), right);
                huffTreeQ.enqueue(combined);
            }

            root = huffTreeQ.dequeue();
        } else if (headerType == STORE_TREE) {
            System.out.println("Type: Tree");
            int size = inStream.readBits(BITS_PER_INT);
            System.out.println("Size: " + size);
            root = rebuildTree(inStream);
        }

        // Get huffcodes with reversed mapping
        Map<String, Integer> codes = generateHuffCodeBackwards(root, "");
        // for (String code : codes.keySet()) {
        // System.out.println(codes.get(code) + " --> " + code);
        // }

        // Go through each bit
        int bit = inStream.readBits(1);
        String currentCode = "";
        boolean shouldContinue = true;

        while (bit != -1 && shouldContinue) {
            // Add bit to current code
            currentCode += Integer.toString(bit);

            // if the currentCode is a key in the map write out its corresponding charachter
            if (codes.containsKey(currentCode)) {
                int characterValue = codes.get(currentCode);
                if (characterValue == PSEUDO_EOF) {
                    shouldContinue = false;
                } else {
                    outStream.write(characterValue);
                    totalBitsWritten += 8;
                    currentCode = "";
                }
            }

            if (shouldContinue) {
                bit = inStream.readBits(1);
            }
        }

        outStream.flush();
        outStream.close();
        inStream.close();

        return totalBitsWritten;
    }

    private TreeNode rebuildTree(BitInputStream inStream) throws IOException {
        // Read the bit
        int bit = inStream.readBits(1);
        // if (bit == -1) {
        // myViewer.showError("Invalid Bit");
        // }
        if (bit == 1) {
            // Leaf node + value
            int value = inStream.readBits(BITS_PER_WORD + 1);
            return new TreeNode(value, 0);
        } else {
            // Internal node
            TreeNode left = rebuildTree(inStream);
            TreeNode right = rebuildTree(inStream);
            return new TreeNode(left, 0, right);
        }
    }

    private Map<String, Integer> generateHuffCodeBackwards(TreeNode curNode, String curPath) {
        Map<String, Integer> huffMap = new HashMap<>();
        if (curNode.isLeaf()) {
            // If it's a leaf node put the ASCII value and its path in the map
            huffMap.put(curPath, curNode.getValue());
        } else {
            // Recursively generate codes for left subtree adding 0 to path
            if (curNode.getLeft() != null) {
                huffMap.putAll(generateHuffCodeBackwards(curNode.getLeft(), curPath + "0"));
            }
            // Recursively generate codes for right subtree adding 1 to path
            if (curNode.getRight() != null) {
                huffMap.putAll(generateHuffCodeBackwards(curNode.getRight(), curPath + "1"));
            }
        }
        return huffMap;
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
