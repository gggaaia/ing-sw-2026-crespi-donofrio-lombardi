package it.polimi.ingsw.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Disk persistence for the controller. Pass 2 only stores one snapshot at
 * a time, in a single file in the server process's working directory.
 * <p>
 * The save is atomic: the snapshot is written to a temporary file first,
 * then moved over the real file with {@link StandardCopyOption#ATOMIC_MOVE}
 * (with a graceful fallback to a regular replace on file systems that
 * don't support atomic moves). This way a server crash mid-write either
 * leaves the previous snapshot intact or leaves the new snapshot fully
 * written; we never see a half-written file.
 */
public class PersistenceManager {

    /** Final on-disk file. */
    public static final String FILENAME = "game-state.bin";

    /** Temporary file used to write the new snapshot before the atomic
     *  rename. */
    public static final String TMP_FILENAME = "game-state.bin.tmp";

    private PersistenceManager() {
        // Utility class: no instances.
    }

    /**
     * Writes the snapshot to disk atomically.
     * <p>
     * Steps:
     * <ol>
     *   <li>Open the tmp file for writing.</li>
     *   <li>Serialize the snapshot into it.</li>
     *   <li>Close the stream.</li>
     *   <li>Atomically move tmp over the real file.</li>
     * </ol>
     *
     * @param snapshot the snapshot to write
     * @throws IOException if any of the I/O steps fail
     */
    public static void save(GameSnapshot snapshot) throws IOException {
        Path tmp = Paths.get(TMP_FILENAME);
        Path target = Paths.get(FILENAME);

        // Step 1-3: write the snapshot into the tmp file.
        OutputStream rawOut = Files.newOutputStream(tmp);
        ObjectOutputStream oos = new ObjectOutputStream(rawOut);
        try {
            oos.writeObject(snapshot);
            oos.flush();
        } finally {
            // Always close, even if writeObject threw.
            try {
                oos.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }

        // Step 4: atomic rename. Some file systems (notably some networked
        // ones) do not support ATOMIC_MOVE; fall back to a plain replace,
        // which is still safer than writing to the target file directly.
        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads back the snapshot previously written by
     * {@link #save(GameSnapshot)}.
     *
     * @return the snapshot
     * @throws IOException             if the file cannot be read
     * @throws ClassNotFoundException  if the snapshot's class layout is
     *                                  no longer compatible with this build
     */
    public static GameSnapshot load() throws IOException, ClassNotFoundException {
        Path target = Paths.get(FILENAME);
        InputStream rawIn = Files.newInputStream(target);
        ObjectInputStream ois = new ObjectInputStream(rawIn);
        try {
            Object o = ois.readObject();
            return (GameSnapshot) o;
        } finally {
            try {
                ois.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }
    }

    /**
     * Deletes the persisted snapshot if any. Used at end-of-game so a
     * future server start sees no leftover state.
     */
    public static void delete() {
        Path target = Paths.get(FILENAME);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best effort: a stale file at worst.
        }
    }

    /**
     * @return {@code true} if a snapshot file exists on disk
     */
    public static boolean exists() {
        return Files.exists(Paths.get(FILENAME));
    }
}
