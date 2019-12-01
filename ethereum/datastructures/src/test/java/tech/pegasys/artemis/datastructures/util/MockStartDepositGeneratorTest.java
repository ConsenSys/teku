/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.util;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.Security;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

class MockStartDepositGeneratorTest {

  private static final String[] PRIVATE_KEYS = {
    "0x0000000000000000000000000000000025295F0D1D592A90B333E26E85149708208E9F8E8BC18F6C77BD62F8AD7A6866",
    "0x0000000000000000000000000000000051D0B65185DB6989AB0B560D6DEED19C7EAD0E24B9B6372CBECB1F26BDFAD000",
    "0x00000000000000000000000000000000315ED405FAFE339603932EEBE8DBFD650CE5DAFA561F6928664C75DB85F97857",
    "0x0000000000000000000000000000000025B1166A43C109CB330AF8945D364722757C65ED2BFED5444B5A2F057F82D391",
    "0x000000000000000000000000000000003F5615898238C4C4F906B507EE917E9EA1BB69B93F1DBD11A34D229C3B06784B",
    "0x00000000000000000000000000000000055794614BC85ED5436C1F5CAB586AAB6CA84835788621091F4F3B813761E7A8",
    "0x000000000000000000000000000000001023C68852075965E0F7352DEE3F76A84A83E7582C181C10179936C6D6348893",
    "0x000000000000000000000000000000003A941600DC41E5D20E818473B817A28507C23CDFDB4B659C15461EE5C71E41F5",
    "0x00000000000000000000000000000000066E3BDC0415530E5C7FED6382D5C822C192B620203CF669903E1810A8C67D06",
    "0x000000000000000000000000000000002B3B88A041168A1C4CD04BDD8DE7964FD35238F95442DC678514F9DADB81EC34"
  };

  private static final String[] EXPECTED_DEPOSITS = {
    "0x30000000A99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4BF2D153F649F7B53359FE8B94A38E44C00FAD2A6BFB0E7F1F0F45460944FBD8DFA7F37DA06A4D13B3983CC90BB46963B0040597307000000600000008684B7F46D25CDD6F937ACDAA54BDD2FB34C78D687DCA93884BA79E60EBB0DF964FAA4C49F3469FB882A50C7726985FF0B20C9584CC1DED7C90467422674A05177B2019661F78A5C5C56F67D586F04FD37F555B4876A910BEDFF830C2BECE0AA",
    "0x30000000B89BEBC699769726A318C8E9971BD3171297C61AEA4A6578A7A4F94B547DCBA5BAC16A89108B6B6A1FE3695D1A874A0B00EC7EF7780C9D151597924036262DD28DC60E1228F4DA6FECF9D402CB3F3594004059730700000060000000A2C86C4F654A2A229A287AABC8C63F224D9FB8E1D77D4A13276A87A80C8B75AA7C55826FEBE4BAE6C826AEECCAA82F370517DB4F0D5EED5FBC06A3846088871696B3C32FF3FDEBDB52355D1EEDE85BCD71AAA2C00D6CF088A647332EDC21E4F3",
    "0x30000000A3A32B0F8B4DDB83F1A0A853D81DD725DFE577D4F4C3DB8ECE52CE2B026ECA84815C1A7E8E92A4DE3D755733BF7E4A9B0036085C6C608E6D048505B04402568C36CCE1E025722DE44F9C3685A5C80FA6004059730700000060000000A5A463D036E9CCB19757B2DDB1E6564A00463AED1EF51BF69264A14B6BFCFF93EB6F63664E0DF0B5C9E6760C560CB58D135265CECBF360A23641AF627BCB17CF6C0541768D3F3B61E27F7C44F21B02CD09B52443405B12FB541F5762CD615D6E",
    "0x3000000088C141DF77CD9D8D7A71A75C826C41A9C9F03C6EE1B180F3E7852F6A280099DED351B58D66E653AF8E42816A4D8F532E005A7DE495BCEC04D3B5E74AE09FFE493A9DD06D7DCBF18C78455571E87D901A0040597307000000600000008731C258353C8AA46A8E38509EECFDC32018429239D9ACAD9B634A4D010CA51395828C0C056808C6E6DF373FEF7E9A570B3D648EC455D90F497E12FC3011148EDED7265B0F995DE72E5982DB1DBB6ECA8275FC99CDD10704B8CF19EC0BB9C350",
    "0x3000000081283B7A20E1CA460EBD9BBD77005D557370CABB1F9A44F530C4C4C66230F675F8DF8B4C2818851AA7D77A80CA5A4A5E004A28C193C65C91B7EBB5B5D14FFA7F75DC48AD4BC66DE82F70FC55A2DF121500405973070000006000000090B20F054F6A2823D66E159050915335E7A4F64BF7AC449EF83BB1D1BA9A6B2385DA977B5BA295EA2D019EE3A8140607079D671352AB233B3BF6BE45C61DCE5B443F23716D64382E34D7676AE64EEDD01BABEEB8BFD26386371F6BC01F1D4539",
    "0x30000000AB0BDDA0F85F842F431BEACCF1250BF1FD7BA51B4100FD64364B6401FDA85BB0069B3E715B58819684E7FC0B10A72A34005856AB195B61DF2FF5D6AB2FA36F30DAB45E42CFA1AAEF3FFD899F29BD864100405973070000006000000099DF72B850141C67FC956A5BA91ABB5A091538D963AA6C082E1EA30B7F7E5A54EC0FF79C749342D4635E4901E8DFC9B90604D5466FF2A7B028C53D4DAC01FFB3AC0555ABD3F52D35AA1ECE7E8E9CCE273416B3CF582A5F2190E87A3B15641F0C",
    "0x300000009977F1C8B731A8D5558146BFB86CAEA26434F3C5878B589BF280A42C9159E700E9DF0E4086296C20B011D2E78C27D373001C5D9BEDBAD1B7AFF3B80E887E65B3357A695B70B6EE0625C2B2F6F86449F8004059730700000060000000A4023F36F4F354F69B615B3651596D4B479F005B04F80EF878AAEB342E94AD6F9ACDDF237309A79247D560B05F4F7139048B5EEE0F08DA3A11F3EE148CA76E3E1351A733250515A61E12027468CFF2DE193AB8EE5CD90BDD1C50E529EDDA512B",
    "0x30000000A8D4C7C27795A725961317EF5953A7032ED6D83739DB8B0E8A72353D1B8B4439427F7EFA2C89CAA03CC9F28F8CBAB8AC001414BFC6DACCA55F974EC910893C8617F9C99DA897534C637B50E9FC69532300405973070000006000000081C52ADA6D975A5B968509AB16FA58D617DD36A6C333E6ED86A7977030E4C5D37A488596C6776C2CDF4831EA7337AD7902020092F60E547714449253A947277681FF80B7BF641CA782214FC9EC9B58C66AB43C0A554C133073C96AD35EDFF101",
    "0x30000000A6D310DBBFAB9A22450F59993F87A4CE5DB6223F3B5F1F30D2C4EC718922D400E0B3C7741DE8E59960F72411A0EE10A700ED09B6181E6F97365E221E70AEEBCB2604011D8C4326F3B98CE8D79B031AE8004059730700000060000000B4AAB8F6624F61F4F5EB6D75839919A3EF6B4E1B19CAE6EF063D6281B60FF1D5EFE02BCBFC4B9EB1038C42E0A3325D8A0FCF7B64FF3CD9DF5C629B864DFDC5B763283254CCD6CFA28CFF53E477FB1743440A18D76A776EC4D66C5F50D695CA85",
    "0x300000009893413C00283A3F9ED9FD9845DDA1CEA38228D22567F9541DCCC357E54A2D6A6E204103C92564CBC05F4905AC7C493A001FE05BAA70DD29CE85F694898BB6DE3BCDE158A825DB56906B54141B2A728D0040597307000000600000009603F7DCAB6822EDB92EB588F1E15FCC685CEB8BCC7257ADB0E4A5995820B8EF77215650792120AFF871F30A52475EA31212AA741A3F0E6B2DBCB3A63181571306A411C772A7FD08826DDEAB98D1C47B5EAD82F8E063B9D7F1F217808EE4FB50",
  };

  private final MockStartDepositGenerator generator = new MockStartDepositGenerator();

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Test
  public void shouldGenerateDepositData() {
    final List<BLSKeyPair> keyPairs =
        Arrays.stream(PRIVATE_KEYS)
            .map(Bytes::fromHexString)
            .map(SecretKey::fromBytes)
            .map(KeyPair::new)
            .map(BLSKeyPair::new)
            .collect(toList());

    final List<DepositData> expectedDeposits =
        Arrays.stream(EXPECTED_DEPOSITS)
            .map(Bytes::fromHexString)
            .map(DepositData::fromBytes)
            .collect(toList());

    final List<DepositData> actualDeposits = generator.createDeposits(keyPairs);
    assertEquals(expectedDeposits, actualDeposits);
  }
}
