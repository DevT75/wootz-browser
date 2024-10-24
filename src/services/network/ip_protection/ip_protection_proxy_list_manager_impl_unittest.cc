// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "services/network/ip_protection/ip_protection_proxy_list_manager_impl.h"

#include <deque>
#include <optional>
#include <utility>
#include <vector>

#include "base/strings/stringprintf.h"
#include "base/test/metrics/histogram_tester.h"
#include "base/test/task_environment.h"
#include "mojo/public/cpp/bindings/receiver.h"
#include "net/base/proxy_chain.h"
#include "services/network/ip_protection/ip_protection_proxy_list_manager.h"
#include "services/network/public/mojom/network_context.mojom.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace network {

namespace {

constexpr char kGetProxyListResultHistogram[] =
    "NetworkService.IpProtection.GetProxyListResult";
constexpr char kProxyListRefreshTimeHistogram[] =
    "NetworkService.IpProtection.ProxyListRefreshTime";

}  // namespace

class MockIpProtectionConfigGetter
    : public network::mojom::IpProtectionConfigGetter {
 public:
  ~MockIpProtectionConfigGetter() override = default;

  // Register an expectation of a call to `TryGetAuthTokens()` returning the
  // given tokens.
  void ExpectTryGetAuthTokensCall(
      uint32_t batch_size,
      std::vector<network::mojom::BlindSignedAuthTokenPtr> bsa_tokens) {
    NOTREACHED_NORETURN();
  }

  // Register an expectation of a call to `TryGetAuthTokens()` returning no
  // tokens and the given `try_again_after`.
  void ExpectTryGetAuthTokensCall(uint32_t batch_size,
                                  base::Time try_again_after) {
    NOTREACHED_NORETURN();
  }

  // Register an expectation of a call to `GetIpProtectionProxyList()`,
  // returning the given proxy list manager.
  void ExpectGetProxyListCall(std::vector<net::ProxyChain> proxy_list) {
    expected_get_proxy_list_calls_.push_back(std::move(proxy_list));
  }

  // Register an expectation of a call to `GetProxyList()`, returning nullopt.
  void ExpectGetProxyListCallFailure() {
    expected_get_proxy_list_calls_.push_back(std::nullopt);
  }

  // True if all expected `TryGetAuthTokens` calls have occurred.
  bool GotAllExpectedMockCalls() {
    return expected_get_proxy_list_calls_.empty();
  }

  // Reset all test expectations.
  void Reset() { expected_get_proxy_list_calls_.clear(); }

  void TryGetAuthTokens(uint32_t batch_size,
                        network::mojom::IpProtectionProxyLayer proxy_layer,
                        TryGetAuthTokensCallback callback) override {
    NOTREACHED_NORETURN();
  }

  void GetProxyList(GetProxyListCallback callback) override {
    ASSERT_FALSE(expected_get_proxy_list_calls_.empty())
        << "Unexpected call to GetProxyList";
    auto& exp = expected_get_proxy_list_calls_.front();
    std::move(callback).Run(std::move(exp));
    expected_get_proxy_list_calls_.pop_front();
  }

 protected:
  std::deque<std::optional<std::vector<net::ProxyChain>>>
      expected_get_proxy_list_calls_;
};

class IpProtectionProxyListManagerImplTest : public testing::Test {
 protected:
  IpProtectionProxyListManagerImplTest()
      : task_environment_(base::test::TaskEnvironment::TimeSource::MOCK_TIME),
        mock_(),
        receiver_(&mock_) {
    remote_ = mojo::Remote<network::mojom::IpProtectionConfigGetter>();
    remote_.Bind(receiver_.BindNewPipeAndPassRemote());
    ipp_proxy_list_ = std::make_unique<IpProtectionProxyListManagerImpl>(
        &remote_,
        /* disable_background_tasks_for_testing=*/true);
  }

  // Wait until the proxy list is refreshed.
  void WaitForProxyListRefresh() {
    ipp_proxy_list_->SetOnProxyListRefreshedForTesting(
        task_environment_.QuitClosure());
    task_environment_.RunUntilQuit();
  }

  // Shortcut to create a ProxyChain from hostnames.
  net::ProxyChain MakeChain(std::vector<std::string> hostnames) {
    std::vector<net::ProxyServer> servers;
    for (auto& hostname : hostnames) {
      servers.push_back(net::ProxyServer::FromSchemeHostAndPort(
          net::ProxyServer::SCHEME_HTTPS, hostname, std::nullopt));
    }
    return net::ProxyChain::ForIpProtection(servers);
  }

  base::test::TaskEnvironment task_environment_{
      base::test::TaskEnvironment::TimeSource::MOCK_TIME};

  MockIpProtectionConfigGetter mock_;

  mojo::Receiver<network::mojom::IpProtectionConfigGetter> receiver_;

  mojo::Remote<network::mojom::IpProtectionConfigGetter> remote_;

  // The IpProtectionProxyListImpl being tested.
  std::unique_ptr<IpProtectionProxyListManagerImpl> ipp_proxy_list_;

  base::HistogramTester histogram_tester_;
};

// The manager gets the proxy list on startup and once again on schedule.
TEST_F(IpProtectionProxyListManagerImplTest, ProxyListOnStartup) {
  std::vector<net::ProxyChain> exp_proxy_list = {MakeChain({"a-proxy"})};
  mock_.ExpectGetProxyListCall(exp_proxy_list);
  ipp_proxy_list_->EnableProxyListRefreshingForTesting();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
  EXPECT_EQ(ipp_proxy_list_->ProxyList(), exp_proxy_list);

  base::Time start = base::Time::Now();
  mock_.ExpectGetProxyListCall({MakeChain({"b-proxy"})});
  WaitForProxyListRefresh();
  base::TimeDelta delay = net::features::kIpPrivacyProxyListFetchInterval.Get();
  EXPECT_EQ(base::Time::Now() - start, delay);

  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
  exp_proxy_list = {MakeChain({"b-proxy"})};
  EXPECT_EQ(ipp_proxy_list_->ProxyList(), exp_proxy_list);
}

// The manager refreshes the proxy list on demand, but only once even if
// `RequestRefreshProxyList()` is called repeatedly.
TEST_F(IpProtectionProxyListManagerImplTest, ProxyListRefresh) {
  std::vector<net::ProxyChain> exp_proxy_list = {MakeChain({"a-proxy"})};
  mock_.ExpectGetProxyListCall(exp_proxy_list);
  ipp_proxy_list_->RequestRefreshProxyList();
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
  EXPECT_EQ(ipp_proxy_list_->ProxyList(), exp_proxy_list);
}

// The manager gets the proxy list on startup and once again on schedule.
TEST_F(IpProtectionProxyListManagerImplTest, IsProxyListAvailableEvenIfEmpty) {
  mock_.ExpectGetProxyListCall({});
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
}

// The manager keeps its existing proxy list if it fails to fetch a new one.
TEST_F(IpProtectionProxyListManagerImplTest, ProxyListKeptAfterFailure) {
  std::vector<net::ProxyChain> exp_proxy_list = {MakeChain({"a-proxy"})};
  mock_.ExpectGetProxyListCall(exp_proxy_list);
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
  EXPECT_EQ(ipp_proxy_list_->ProxyList(), exp_proxy_list);

  // Fast-forward long enough that we can fetch again
  task_environment_.FastForwardBy(
      net::features::kIpPrivacyProxyListMinFetchInterval.Get());

  mock_.ExpectGetProxyListCallFailure();
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  EXPECT_TRUE(ipp_proxy_list_->IsProxyListAvailable());
  EXPECT_EQ(ipp_proxy_list_->ProxyList(), exp_proxy_list);
}

TEST_F(IpProtectionProxyListManagerImplTest, GetProxyListFailureRecorded) {
  mock_.ExpectGetProxyListCallFailure();
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  histogram_tester_.ExpectUniqueSample(
      kGetProxyListResultHistogram,
      IpProtectionProxyListManagerImpl::ProxyListResult::kFailed, 1);
  histogram_tester_.ExpectTotalCount(kProxyListRefreshTimeHistogram, 0);
}

TEST_F(IpProtectionProxyListManagerImplTest, GotEmptyProxyListRecorded) {
  mock_.ExpectGetProxyListCall({});
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  histogram_tester_.ExpectUniqueSample(
      kGetProxyListResultHistogram,
      IpProtectionProxyListManagerImpl::ProxyListResult::kEmptyList, 1);
  histogram_tester_.ExpectTotalCount(kProxyListRefreshTimeHistogram, 1);
}

TEST_F(IpProtectionProxyListManagerImplTest, GotPopulatedProxyListRecorded) {
  mock_.ExpectGetProxyListCall({MakeChain({"a-proxy", "b-proxy"})});
  ipp_proxy_list_->RequestRefreshProxyList();
  WaitForProxyListRefresh();
  ASSERT_TRUE(mock_.GotAllExpectedMockCalls());
  histogram_tester_.ExpectUniqueSample(
      kGetProxyListResultHistogram,
      IpProtectionProxyListManagerImpl::ProxyListResult::kPopulatedList, 1);
  histogram_tester_.ExpectTotalCount(kProxyListRefreshTimeHistogram, 1);
}

}  // namespace network
