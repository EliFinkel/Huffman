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
    private TreeNode huffmanTree;
    private Map<Integer, String> huffCodes;

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
        PriorityQueue<TreeNode> huffTree = new PriorityQueue<>();
        this.header = headerFormat;
        BitInputStream inStream = new BitInputStream(in);
        HashMap<Integer, Integer> freqMap = createFreqMap(inStream);

        // Creates priority queue with each char
        for (Integer asciiVal : freqMap.keySet()) {
            int charFreq = freqMap.get(asciiVal);
            TreeNode charNode = new TreeNode(asciiVal, charFreq);
            huffTree.enqueue(charNode);
        }

        // Makes huffTree
        while (huffTree.size() > 1) {
            TreeNode left = huffTree.dequeue(); // Assume dequeue fetches and removes the smallest element
            TreeNode right = huffTree.dequeue(); // Fetch the next smallest element

            TreeNode combined = new TreeNode(left, left.getFrequency() + right.getFrequency(), right);
            huffTree.enqueue(combined); // Add the combined node back to the queue
        }

        // The last remaining node is the root of the Huffman tree
        TreeNode huffmanTreeRoot = huffTree.dequeue();
        this.huffmanTree = huffmanTreeRoot;

        Map<Integer, String> huffCodes = generateHuffCode(huffmanTreeRoot, "");
        
        this.huffCodes = huffCodes;

        // TODO: Count bits saved
        return 0;
    }

    private HashMap<Integer, Integer> createFreqMap(BitInputStream inStream) throws IOException {
        // HashMap<String, Integer> freqMap = new HashMap<>();
        HashMap<Integer, Integer> freqMap = new HashMap<>();

        int curVal = inStream.read();
        while (curVal != -1) {
            // String letter = Character.toString((char) curVal);
            freqMap.put(curVal, freqMap.getOrDefault(curVal, 0) + 1);
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

        return 0;
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
        throw new IOException("uncompress not implemented");
        // return 0;
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
