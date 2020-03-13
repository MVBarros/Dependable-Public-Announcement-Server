package dpas.common.domain;

import dpas.common.domain.exception.NullAnnouncementException;
import dpas.common.domain.exception.NullUserException;

import java.util.ArrayList;

public class GeneralBoard extends AnnouncementBoard {
    private ArrayList<Announcement> _posts;
    private int _sequenceNumber = 0;

    @Override
    public void post(Announcement announcement) throws NullAnnouncementException, NullUserException {
        if (announcement == null) {
            throw new NullAnnouncementException();
        }
        _posts.add(announcement);
        announcement.set_sequenceNumber(_sequenceNumber);
        _sequenceNumber++;
    }



}
