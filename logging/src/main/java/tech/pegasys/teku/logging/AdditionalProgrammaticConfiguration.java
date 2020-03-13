/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.logging;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.status.StatusLogger;

public class AdditionalProgrammaticConfiguration extends XmlConfiguration {

  public AdditionalProgrammaticConfiguration(
      final LoggerContext loggerContext, final ConfigurationSource configSource) {
    super(loggerContext, configSource);
  }

  @Override
  public Configuration reconfigure() {
    final Configuration refresh = super.reconfigure();

    if (refresh != null && AbstractConfiguration.class.isAssignableFrom(refresh.getClass())) {
      LoggingConfigurator.addLoggersProgrammatically((AbstractConfiguration) refresh);
    } else {
      StatusLogger.getLogger().warn("Cannot programmatically reconfigure loggers");
    }

    return refresh;
  }
}
