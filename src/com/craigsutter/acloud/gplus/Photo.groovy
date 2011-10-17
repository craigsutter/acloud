package com.craigsutter.acloud.gplus

import com.google.gdata.client.photos.PicasawebService
import com.google.gdata.data.media.mediarss.MediaContent
import com.google.gdata.data.photos.AlbumEntry
import com.google.gdata.data.photos.AlbumFeed
import com.google.gdata.data.photos.PhotoEntry
import com.google.gdata.data.photos.UserFeed
import javax.script.ScriptEngineManager
import javax.script.ScriptEngine

class Photo {

    private String user
    private String password
    PicasawebService picasawebService

    public Photo(user,password){
        picasawebService = new PicasawebService("craigsutter-acloud-1.0");
        picasawebService.setUserCredentials(user,password)
        this.user=user
        this.password=password
    }

    public AlbumEntry getInstantUploadAlbum(){
        URL feedUrl = new URL("https://picasaweb.google.com/data/feed/api/user/${user}?kind=album")

        UserFeed myUserFeed = picasawebService.getFeed(feedUrl, UserFeed.class)
        Collection albums = myUserFeed.getAlbumEntries().findAll{
            it.getTitle().getPlainText().toLowerCase() == "instant upload"
        }

        if (albums != null && albums.size()==1)
            return albums[0]
        else
            return null
    }

    public List getRecentlyUploadedPics(AlbumEntry album){
        //Probably need to change this to a query, get max 1 record back and see if have recent entries newer than timestamp
        Date lastTimeStamp = getLastTimeStamp(album)

        AlbumFeed feed = picasawebService.getFeed(new URL(album.feedLink.href), AlbumFeed.class)
        List photos= feed.getPhotoEntries().sort {a,b->
            a.getTimestamp() == b.getTimestamp()? 0 : (a.getTimestamp() < b.getTimestamp() ? -1 : 1)
        }.findAll() {ph->
            return  lastTimeStamp==null || ph.timestamp > lastTimeStamp
        }

        if (photos.size() > 0){
            PhotoEntry pe = photos[photos.size()-1]
            setLastTimeStamp(album,pe.getTimestamp())
        }

        return photos
    }

    public void downloadPhotos(List photoEntries){
        println "Downloading " + photoEntries.size() + " photos"
        File picDir = new File("pics")
        if (picDir.exists())
            picDir.deleteDir()

        picDir.mkdir()

        photoEntries.each{PhotoEntry ph->
            ph.getMediaGroup().getContents().each() {MediaContent mc ->
                if (mc.type == "image/jpeg" || mc.type == "video/mpeg4"){
                    File pic = new File("${picDir}/${ph.title.plainText}")
                    if (!pic.exists()){
                        println "Downloading " + ph.getTimestamp() + ": " + ph.getTitle().plainText
                            pic.withOutputStream { out ->
                                out << new URL(mc.getUrl()).openStream()
                            }
                    }
                }
            }
        }
    }

    public void importPhotosToiPhoto(){
        File pics = new File("pics")
        String importDir = pics.absolutePath

        def importPhotos = """\
            tell application \"iPhoto\"
	            import from \"${importDir}\" to (the first album whose name is \"Your Phone\")
            end tell
        """
        println importPhotos

        ScriptEngineManager mgr = new ScriptEngineManager()
        ScriptEngine engine = mgr.getEngineByName("AppleScript")
        engine.eval(importPhotos)

    }

    public Date getLastTimeStamp(AlbumEntry album){
        File tsFile = new File(getAlbumTitle(album) + ".timestamp")
        return tsFile.exists() ? new Date(tsFile.getText()) : null
    }

    public void setLastTimeStamp(AlbumEntry album,Date lastTimeStamp){
        File tsFile = new File(getAlbumTitle(album) + ".timestamp")
        tsFile.write(lastTimeStamp.toString())
    }

    private String getAlbumTitle(AlbumEntry album){
        return album.title.plainText.replace(' ','').toLowerCase()
    }

}
