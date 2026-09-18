import http from "node:http";
import { installFailureRecovery, createIdempotencyStore } from "./reliability.js";
import { createBackgroundJobQueue } from "./backgroundJobs.js";
import { registerMarketplaceSettlementRoutes } from "./marketplaceSettlement.js";
import { registerMarketplacePayoutRetryRoutes } from "./marketplacePayoutRetry.js";
import { registerMarketplaceSettlementWorker } from "./marketplaceSettlementWorker.js";
import { registerMarketplacePaymentExpiryWorker } from "./marketplacePaymentExpiry.js";
import { registerMarketplaceInspectionExpiryWorker } from "./marketplaceInspectionExpiry.js";
import { registerMarketplaceProtectionRoutes } from "./marketplaceProtection.js";
import { registerMarketplaceProtectionResolutionRoutes } from "./marketplaceProtectionResolution.js";
import { installSecurityHardening } from "./securityHardening.js";
import { registerRealtimeAssistantRoutes } from "./aiRealtimeRoutes.js";
import { registerPrivacyRoutes } from "./privacyRoutes.js";
import { registerProfileRoutes } from "./profileRoutes.js";
import { registerFollowRoutes } from "./followRoutes.js";
import { registerGroupRoutes } from "./groupRoutes.js";
import { registerGroupMembershipRoutes } from "./groupMembershipRoutes.js";
import { registerGroupInviteRoutes } from "./groupInviteRoutes.js";
import { registerGroupContentRoutes } from "./groupContentRoutes.js";
import { registerStatusManagementRoutes } from "./statusManagementRoutes.js";
import { registerStatusInteractionRoutes } from "./statusInteractionRoutes.js";
import { registerNotificationPreferenceRoutes } from "./notificationPreferences.js";
import { registerNotificationDeviceRoutes } from "./notificationDevices.js";
import { registerAdminRoutes } from "./adminRoutes.js";
import { registerMonetizationRoutes } from "./monetizationRoutes.js";
import { installPresencePrivacyGuard } from "./presencePrivacy.js";
import { installMediaPrivacyGuard } from "./mediaPrivacy.js";
import { installSocialHardening } from "./socialHardening.js";
import { installPrivateCachePolicy } from "./privateCachePolicy.js";
import { installMarketplaceMediaPrivacyGuard } from "./marketplaceMediaPrivacy.js";
import { installRequestResourceGuard } from "./requestResourceGuard.js";
import { installApiAbuseGuard } from "./apiAbuseGuard.js";
import { registerMarketplaceBatch2FinalHardening } from "./marketplaceBatch2FinalHardening.js";
import { registerFynxAiRoutes } from "./fynxAiToolRegistry.js";
import { registerFynxAiConversationRoutes } from "./aiConversationRoutes.js";
import { installPeopleResponseHardening } from "./peopleResponseHardening.js";
import { registerR6GIntegrationRoutes } from "./r6gIntegrationRoutes.js";

installPresencePrivacyGuard();
const originalCreateServer = http.createServer;
http.createServer = function fynxCreateServer(...args) {
  const app = args[0];
  let routeRegistrationReady = Promise.resolve();
  if (app && typeof app.use === "function") {
    registerRealtimeAssistantRoutes({ app });
    registerFynxAiRoutes({ app });
    registerFynxAiConversationRoutes({ app });

    let resolveRouteRegistration;
    let rejectRouteRegistration;
    routeRegistrationReady = new Promise((resolve, reject) => {
      resolveRouteRegistration = resolve;
      rejectRouteRegistration = reject;
    });

    // Keep the existing deferred registration architecture, but gate request
    // dispatch until every production route and guard has been installed.
    // This prevents cold-start 404/unguarded-request races after listen().
    app.use(async (_req, res, next) => {
      try {
        await routeRegistrationReady;
        return next();
      } catch (error) {
        console.error("[fynx-startup] route registration failed", error);
        return res.status(503).json({ error: "server is still starting" });
      }
    });

    setImmediate(() => {
      try {
        installSecurityHardening({ app });
        installRequestResourceGuard(app);
        installApiAbuseGuard(app);
        registerMarketplaceSettlementRoutes({ app });
        registerMarketplacePayoutRetryRoutes({ app });
        registerMarketplaceProtectionRoutes({ app });
        registerMarketplaceProtectionResolutionRoutes({ app });
        registerProfileRoutes({ app });
        registerPrivacyRoutes({ app });
        registerFollowRoutes({ app });
        registerGroupRoutes({ app });
        registerGroupMembershipRoutes({ app });
        registerGroupInviteRoutes({ app });
        registerGroupContentRoutes({ app });
        registerR6GIntegrationRoutes({ app });
        registerStatusManagementRoutes({ app });
        registerStatusInteractionRoutes({ app });
        registerNotificationPreferenceRoutes({ app });
        registerNotificationDeviceRoutes({ app });
        registerAdminRoutes({ app });
        registerMonetizationRoutes({ app });
        installMediaPrivacyGuard(app);
        installSocialHardening(app);
        installPrivateCachePolicy(app);
        installMarketplaceMediaPrivacyGuard(app);
        installPeopleResponseHardening(app);
        resolveRouteRegistration();
      } catch (error) {
        rejectRouteRegistration(error);
      }
    });
  }
  const server = originalCreateServer.apply(this, args);
  server.keepAliveTimeout=65_000;
  server.headersTimeout=70_000;
  server.requestTimeout=30_000;
  server.maxRequestsPerSocket=1_000;
  server.maxConnections=500;
  let requests=0; let completed=0; let totalLatencyMs=0; let errors=0;
  server.on("request",(_req,res)=>{const startedAt=process.hrtime.bigint();requests+=1;res.on("finish",()=>{completed+=1;totalLatencyMs+=Number(process.hrtime.bigint()-startedAt)/1_000_000;if(res.statusCode>=500)errors+=1;});});
  const report=setInterval(()=>{if(!completed)return;const averageLatencyMs=totalLatencyMs/completed;console.log(`[fynx-metrics] requests=${requests} completed=${completed} errors5xx=${errors} avgLatencyMs=${averageLatencyMs.toFixed(1)}`);},60_000);report.unref();
  server.on("error",error=>{console.error("[fynx-http] server error",error);});
  const recovery=installFailureRecovery({server,pool:globalThis.__fynxPool||null,logger:console});
  globalThis.__fynxRecovery=recovery;
  globalThis.__fynxIdempotency=createIdempotencyStore({maxEntries:10_000,ttlMs:24*60*60*1000});
  globalThis.__fynxJobHandlers=globalThis.__fynxJobHandlers||{};
  const jobs=createBackgroundJobQueue({logger:console});
  globalThis.__fynxBackgroundJobs=jobs;
  registerMarketplaceSettlementWorker({ jobs, logger: console });
  registerMarketplacePaymentExpiryWorker({ logger: console });
  registerMarketplaceInspectionExpiryWorker({ logger: console });
  registerMarketplaceBatch2FinalHardening({ logger: console });
  void jobs.start().catch(error=>console.error("[fynx-jobs] startup failed",error?.message||error));
  return server;
};
