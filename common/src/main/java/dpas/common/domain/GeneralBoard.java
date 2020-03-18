package dpas.common.domain;

import dpas.common.domain.exception.NullAnnouncementException;

import java.io.Serializable;

public class GeneralBoard extends AnnouncementBoard implements Serializable {

    @Override
    public void post(Announcement announcement) throws NullAnnouncementException {
        if (announcement == null) {
            throw new NullAnnouncementException("Invalid Announcement: Cannot be null");
        }
        _posts.add(announcement);

    }


}
