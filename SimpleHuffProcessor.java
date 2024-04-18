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
        this.header = headerFormat;
        BitInputStream inStream = new BitInputStream(in);
        // Map to store ascii value of char and its freqs
        HashMap<Integer, Integer> freqMap = createFreqMap(inStream);
        TreeNode huffmanTreeRoot = getHuffmanTreeRoot(freqMap);
        this.huffTreeRoot = huffmanTreeRoot;
        // Generate Huffman codes from the tree root
        Map<Integer, String> huffCodes = generateHuffCode(this.huffTreeRoot, "");

        this.huffCodes = huffCodes;
        calcCompFileSize(headerFormat, freqMap, huffmanTreeRoot, huffCodes);
        return uncompSize - compSize;
    }

    /*
     * Helper method that calculates what the size of the compressed file will be
     * 
     * @param headerFormat the header type
     * 
     * @param freqMap a map of ascii values and corresponding freqs
     * 
     * @param huffmanTreeRoot the root of the huffman tree
     * 
     * @param huffCodes a map of ascii values and corresponding hufff codes
     */
    private void calcCompFileSize(int headerFormat, HashMap<Integer, Integer> freqMap,
            TreeNode huffmanTreeRoot, Map<Integer, String> huffCodes) {
        // loop through all charachters
        for (Integer asciiVal : huffCodes.keySet()) {
            int codeLength = huffCodes.get(asciiVal).length();
            if (asciiVal == PSEUDO_EOF) {
                // Add the length of the PSUEDO_EOF
                this.compSize += codeLength;
            } else {
                // Add the length of each huff code multiplied by its freq in the file
                int codeFreq = freqMap.get(asciiVal);
                this.compSize += codeLength * codeFreq;
            }
        }
        // Account for magic number and header type and header data
        this.compSize += BITS_PER_INT * 2;
        if (headerFormat == STORE_COUNTS) {
            this.compSize += ALPH_SIZE * BITS_PER_INT;
        } else if (headerFormat == STORE_TREE) {
            updateHeaderInfoForTree(huffmanTreeRoot);
            this.compSize += BITS_PER_INT;
        }
    }

    /*
     * Helper method that creates a huffman tree from a frequency map
     * 
     * @param freqMap a map of ascii values and their frequency in the file
     * 
     * @return the root of the new huffman tree
     */
    private TreeNode getHuffmanTreeRoot(HashMap<Integer, Integer> freqMap) {
        // Create empty priority queue
        PriorityQueue<TreeNode> huffTreeQ = new PriorityQueue<>();
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
        return huffmanTreeRoot;
    }

    /*
     * A recursive helper method that calculates the number of bits that will
     * be used for STORE_TREE header data
     * 
     * @param curNode the current node we are traversing
     */
    private void updateHeaderInfoForTree(TreeNode curNode) {
        // Return if we fell out of the tree
        if (curNode == null) {
            return;
        }
        if (curNode.isLeaf()) {
            // Add 1 bit and the data for a leaf node
            this.compSize += BITS_PER_WORD + 2;
        } else {
            // Add one bit if internal node
            this.compSize += 1;
            // Recurse both left and right subtrees
            updateHeaderInfoForTree(curNode.getLeft());
            updateHeaderInfoForTree(curNode.getRight());
        }
    }

    /*
     * Helper method that creates a frequency mapping of ascii vals and their
     * frequency
     * in the file
     * 
     * @param inStream the BitInputStream than contains our files data
     * 
     * @return the a map of all the freqs for every charachter in the file
     */
    private HashMap<Integer, Integer> createFreqMap(BitInputStream inStream) throws IOException {
        // HashMap to store ascii values and freqs
        HashMap<Integer, Integer> freqMap = new HashMap<>();
        // Map of char freqs to be sent when STORE_COUNTS
        this.freqs = new int[ALPH_SIZE];
        // Read everyvalue and add or update its frequency in the map
        int curVal = inStream.read();
        while (curVal != -1) {
            // String letter = Character.toString((char) curVal);
            freqMap.put(curVal, freqMap.getOrDefault(curVal, 0) + 1);
            freqs[curVal]++;
            curVal = inStream.read();
        }
        return freqMap;
    }

    /*
     * A recursive helper method that generates all the huff codes from the hufftree
     * 
     * @param curNode starts at the root and becomes the node we are traversing
     * 
     * @param curPath is the current path while we traverse to the leaf nodes
     * 
     * @return a map of ascii values and their respective huffman codes
     */
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

        if (!force && this.compSize >= this.uncompSize) {
            inStream.close();
            outStream.close();
            myViewer.showError("Cannot compress file as compressed will be larger than original");
            return 0;
        }
        this.writtenBitNum = 0;
        // Write out the magic number
        outStream.writeBits(BITS_PER_INT, MAGIC_NUMBER);
        this.writtenBitNum += BITS_PER_INT;

        if (this.header == STORE_COUNTS) {
            handleStoreCounts(outStream);
        } else if (this.header == STORE_TREE) {
            handleStoreTree(outStream);
        }
        writeOutDataCompressed(inStream, outStream);
        finishCompress(inStream, outStream);
        return this.writtenBitNum;
    }

    /*
     * A helper method that writes out the end of file code and closes the streams
     * 
     * @param inStream the BitInputStream to close
     * 
     * @param outStream the BitOutputStream to close
     */
    private void finishCompress(BitInputStream inStream, BitOutputStream outStream) {
        // Write PSUEDO_EOF
        String eofStr = huffCodes.get(PSEUDO_EOF);
        int eofBitNum = eofStr.length();
        int eofVal = Integer.parseInt(eofStr, 2);

        // Write out the PSUEDO_EOF huff code
        outStream.writeBits(eofBitNum, eofVal);
        this.writtenBitNum += eofBitNum;

        myViewer.showMessage("Wrote: " + this.writtenBitNum);
        outStream.close();
        inStream.close();
    }

    /*
     * A helper method that compares each charachter in the original file to its
     * huff code
     * and writes the huff code
     * 
     * @param inStream the BitInputStream we are reading from
     * 
     * @param outStream the BitOutputStream we are writing with
     */
    private void writeOutDataCompressed(BitInputStream inStream, BitOutputStream outStream) throws IOException {
        // Write out data coded with the huffman codes
        int curVal = inStream.read();
        while (curVal != -1) {
            String huffStr = huffCodes.get(curVal);
            int bitNum = huffStr.length();
            // Turn the huffman code string to and int
            int huffVal = Integer.parseInt(huffStr, 2);
            // Write out the huffcode
            outStream.writeBits(bitNum, huffVal);
            this.writtenBitNum += bitNum;
            // Iterate to the next charachter
            curVal = inStream.read();
        }
    }

    /*
     * A helper method to write out the header and header data when header type is
     * STORE_TREE
     * 
     * @param outStream the BitOutputStream we are writing data from
     */
    private void handleStoreTree(BitOutputStream outStream) {
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

    /*
     * A helper method that writes out the header and header data when the header
     * type
     * is STORE_COUNTS
     * 
     * @param outStream the BitOutputStream we are writing from
     */
    private void handleStoreCounts(BitOutputStream outStream) {
        // Write out the header type
        outStream.writeBits(BITS_PER_INT, STORE_COUNTS);
        this.writtenBitNum += BITS_PER_INT;

        // Write out the freq at each index in freqs
        for (int k = 0; k < ALPH_SIZE; k++) {
            outStream.writeBits(BITS_PER_INT, this.freqs[k]);
            this.writtenBitNum += BITS_PER_INT;
        }
    }

    /*
     * A recursive helper method that determines the size of the flattened huff tree
     * so we can add the size as a 32bit int before we write out the flattened tree
     * 
     * @param curNode the current node we are traversing
     * 
     * @param size an int array to keep track of the size
     */
    private int calcTreeSize(TreeNode curNode, int[] size) {
        if (curNode == null) {
            return 0;
        }
        if (curNode.isLeaf()) {
            size[0] += 1;
            size[0] += BITS_PER_INT;
        } else {
            size[0] += 1;
            calcTreeSize(curNode.getLeft(), size);
            calcTreeSize(curNode.getRight(), size);
        }
        return size[0];
    }

    /*
     * A recursive helper method that performs a pre-order traversal and writes out
     * the
     * huff tree with 0 for internal nodes and 1 + ascii value for leaf nodes
     * 
     * @param curNode the current node we are traversing
     * 
     * @param outStream the BitOutputStream we are writing data from
     */
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

        // Read the magic number to validate the file
        checkMagic(inStream, outStream);
        // Read the header type
        int headerType = inStream.readBits(BITS_PER_INT);
        // Create a arbitrary node to store root
        TreeNode root = new TreeNode(0, 0);
        if (headerType == STORE_COUNTS) {
            root = uncompStoreCount(inStream);
        } else if (headerType == STORE_TREE) {
            // Accound for 32 bits for size of tree
            inStream.readBits(BITS_PER_INT);
            root = rebuildTree(inStream);
        }
        // Get huffcodes with reversed mapping
        Map<String, Integer> codes = generateHuffCodeBackwards(root, "");
        // Go through each bit
        int totalBitsWritten = writeUncompData(inStream, outStream, codes);

        outStream.flush();
        outStream.close();
        inStream.close();

        return totalBitsWritten;
    }

    /*
     * A helper method that will write out the converted data from huffcode to
     * charachter
     * 
     * @param inStream the BitInputStream we are reading from
     * 
     * @param outStream the ButOutputStream we are writing data from
     * 
     * @param codes a map of huffcodes to ascii values
     * 
     * @return the number of bits written by the method
     */
    private int writeUncompData(BitInputStream inStream, BitOutputStream outStream,
            Map<String, Integer> codes) throws IOException {
        int totalBitsWritten = 0;
        int bit = inStream.readBits(1);
        String currentCode = "";
        boolean shouldContinue = true;
        while (bit != -1 && shouldContinue) {
            // Add bit to current code
            currentCode += Integer.toString(bit);
            // if the currentCode is a key in the map write out its corresponding charachter
            if (codes.containsKey(currentCode)) {
                // Get the value the char(ascii val) or PSUEDO_EOF huff code is mapped to
                int characterValue = codes.get(currentCode);
                if (characterValue == PSEUDO_EOF) {
                    // Stop the loop if we read in the PSUEDO_EOF val
                    shouldContinue = false;
                } else {
                    outStream.write(characterValue);
                    totalBitsWritten += BITS_PER_WORD;
                    currentCode = "";
                }
            }
            if (shouldContinue) {
                bit = inStream.readBits(1);
            }
        }
        return totalBitsWritten;
    }

    /*
     * A helper method that rebuilds the huff tree when the header type is
     * STORE_COUNTS
     * 
     * @param inStream the BitInputStream we are reading from
     * 
     * @return the root of the huff tree we rebuilt
     */
    private TreeNode uncompStoreCount(BitInputStream inStream) throws IOException {
        TreeNode root;
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
        return root;
    }

    /*
     * A helper method that checks if the magic number at the beggining of
     * the .hf file is correct
     * 
     * @param inStream the BitInputStream we are reading from
     * @param outStream the BitOutputStream we are writing from
     */
    private void checkMagic(BitInputStream inStream, BitOutputStream outStream) throws IOException {
        int magic = inStream.readBits(BITS_PER_INT);
        if (magic != MAGIC_NUMBER) {
            inStream.close();
            outStream.close();
            throw new IOException("Invalid magic number file cant be compressed!");
        }
    }

    /*
     * A recursive helper method that rebuilds the tree when the header is STORE_TREE
     * 
     * @param inStream the BitInputStream we are reading from
     * 
     * @return the root of the rebuilt huffTree
     */
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

    /*
     * A recursive helper method similar to generateHuffCodes() but mappings are reverse
     * 
     * @param curNode the current node we are traversing
     * @param curPath the current path to the curNode
     * 
     * @ return a mapping of huffcodes to ascii values
     */
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
