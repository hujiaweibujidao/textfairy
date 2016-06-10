package imagepicker.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * 相册
 *
 * Created by yazeed44 on 6/14/15.
 */
public class AlbumEntry implements Serializable {

    public final int id;
    public final String name;
    public ImageEntry coverImage;
    public final ArrayList<ImageEntry> imageList = new ArrayList<>();

    public AlbumEntry(int albumId, String albumName) {
        this.id = albumId;
        this.name = albumName;
    }

    public void addPhoto(ImageEntry imageEntry) {
        imageList.add(imageEntry);
    }

    public void sortImagesByTimeDesc() {
        Collections.sort(imageList, new Comparator<ImageEntry>() {
            @Override
            public int compare(ImageEntry lhs, ImageEntry rhs) {
                return (int) (rhs.dateAdded - lhs.dateAdded);
            }
        });
        coverImage = imageList.get(0);//
    }
}
