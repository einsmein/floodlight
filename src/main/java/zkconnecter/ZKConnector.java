package zkconnecter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;


import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;

public class ZKConnector {
	// declare zookeeper instance to access ZooKeeper ensemble
	   private ZooKeeper zoo;
	   final CountDownLatch connectedSignal = new CountDownLatch(1);
	   
	   // Method to connect zookeeper ensemble.
	   public ZooKeeper connect(String host) throws IOException,InterruptedException {
		
	      zoo = new ZooKeeper(host,5000,new Watcher() {
			
	         public void process(WatchedEvent we) {

	            if (we.getState() == KeeperState.SyncConnected) {
	               connectedSignal.countDown();
	            }
	         }
	      });
			
	      connectedSignal.await();
	      return zoo;
	   }
	   

	   // Method to disconnect from zookeeper server
	   public void close() throws InterruptedException {
	      zoo.close();
	   }
	   

	   // Method to create znode in zookeeper ensemble
	   public void create(String path, byte[] data) throws 
	      KeeperException,InterruptedException {
	      zoo.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
	   }
	   
	   // Method to check existence of znode and its status, if znode is available.
	   public Stat znode_exists(String path) throws
	      KeeperException,InterruptedException {
	      return zoo.exists(path, true);
	   }

	   // Method to update the data in a znode. Similar to getData but without watcher.
	   public void update(String path, byte[] data) throws
	      KeeperException,InterruptedException {
	      zoo.setData(path, data, zoo.exists(path,true).getVersion());
	   }
	   
	   // Method to check existence of znode and its status, if znode is available.
	   public void delete(String path) throws KeeperException,InterruptedException {
	      zoo.delete(path,zoo.exists(path,true).getVersion());
	   }
	   
//	   try {
//	         conn = new ZooKeeperConnection();
//	         zk = conn.connect("localhost");
//	         create(path, data); // Create the data to the specified path
//	         conn.close();
//	      } catch (Exception e) {
//	         System.out.println(e.getMessage()); //Catch error message
//	      }
}
