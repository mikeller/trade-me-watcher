TradeMe Watcher
===============

This little java app periodically polls data from the TradeMe online auction platform. It can be configured to execute custom searrchess, and it also polls for new questions on auctions on the watch list of the authenticated user. 


Technological Overview:

* OAuth for user authentication on TradeMe;
* built to to be run as a stand alone app or deployed onto Cloud Foundry;
* uses java mail API or SendGrid to send notification emails;
* stores cache data in local preferences or in a mysql DB.

Have fun!
