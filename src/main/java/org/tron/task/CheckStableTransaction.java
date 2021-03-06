package org.tron.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.config.Args;
import org.tron.service.WalletGrpcClient;

@Slf4j
public class CheckStableTransaction implements Task {

  private List<WalletGrpcClient> clients = new ArrayList<>();

  private List<String> grpcHosts;

  public CheckStableTransaction(List<String> grpcHosts) {
    this.grpcHosts = grpcHosts;
  }

  @Override
  public void init(Args args) {
    logger.info("Init check stable transaction task.");

    initGrpcClients();
  }

  private void initGrpcClients() {
    logger.info("Init gRPC clients.");

    for (String host : this.grpcHosts) {
      WalletGrpcClient client = new WalletGrpcClient(
          host);

      clients.add(client);
    }
  }

  @Override
  public void start() {
    logger.info("Start check stable transaction task.");

    Map<String, List<Long>> result = new HashMap<>();

    do {
      boolean needCheck = false;
      for (WalletGrpcClient client : clients) {
        long num = client.getTotalTransaction().getNum();

        List<Long> nums = result.get(client.getHost());

        if (null == nums || nums.isEmpty()) {
          List<Long> ns = new ArrayList<>();
          ns.add(num);
          result.put(client.getHost(), ns);
        } else if (nums.size() < 3) {
          nums.add(num);
        } else {
          nums.remove(0);
          nums.add(num);
          needCheck = true;
        }
      }

      boolean isStable = true;
      if (needCheck) {
        Set<Entry<String, List<Long>>> entries = result.entrySet();
        for (Entry<String, List<Long>> entry : entries) {
          List<Long> nums = entry.getValue();
          long first = nums.get(0);
          long second = nums.get(1);
          long third = nums.get(2);

          if (first != second || first != third) {
            isStable = false;
            break;
          }
        }
      }

      if (isStable && needCheck) {
        break;
      }

      try {
        logger.info("Need check stable transaction.");
        Set<Entry<String, List<Long>>> entries = result.entrySet();
        for (Entry<String, List<Long>> entry : entries) {
          List<Long> nums = entry.getValue();
          StringBuilder tips = new StringBuilder();
          for (Long n : nums) {
            tips.append(n).append(" ");
          }

          logger.info("{}: {}", entry.getKey(), tips.toString());
        }
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        logger.error(e.getMessage());
        Thread.currentThread().interrupt();
      }
    } while (true);
  }

  @Override
  public void shutdown() {
    logger.info("Shutdown check stable transaction task.");

    for (WalletGrpcClient client : clients) {
      try {
        client.shutdown();
      } catch (InterruptedException e) {
        logger.error(e.getMessage());
        Thread.currentThread().interrupt();
      }
    }
  }
}
