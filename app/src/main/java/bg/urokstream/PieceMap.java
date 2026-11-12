package bg.urokstream;

public final class PieceMap {
    public final long offset;
    public final long length;
    public final int pieceLength;
    public final int first;
    public final int last;

    public PieceMap(long offset, long length, int pieceLength) {
        if (offset < 0 || length <= 0 || pieceLength <= 0 || offset > Long.MAX_VALUE - length)
            throw new IllegalArgumentException("Invalid file bounds");
        this.offset = offset; this.length = length; this.pieceLength = pieceLength;
        first = (int) (offset / pieceLength);
        last = (int) ((offset + length - 1) / pieceLength);
    }

    public int pieceAt(long position) {
        if (position < 0 || position >= length) throw new IllegalArgumentException("Position outside video");
        return (int) ((offset + position) / pieceLength);
    }

    /** Read only within a hash-verified piece; never return the sparse file's zero-filled gaps. */
    public int readable(long position, int requested) {
        if (requested < 0) throw new IllegalArgumentException("Negative read");
        pieceAt(position);
        return (int) Math.min(Math.min((long) requested, length - position), pieceLength - (offset + position) % pieceLength);
    }
}
