package com.stupidbeauty.msclearnfootball;

import java.util.ArrayList;

@SuppressWarnings({"EmptyMethod", "unused", "CanBeFinal", "MismatchedQueryAndUpdateOfCollection"})
public class VoiceRecognizeResult
{
	public boolean isLs() {
		return ls;
	}

	boolean ls=false; //!<是否是最终结果。

	public ArrayList<VoiceRecognizeResultWsCw> getFirstCwList() //获取第一个cw列表。
    {
        return ws.get(0).getCw(); //获取cw列表。
    } //public ArrayList<VoiceRecognizeResultWsCw> getFirstCwList()

	/**
	 * 获取说出的内容。
	 * @return 说出的内容。
	 */
	public String getFirstSaidText()
	{
		return ws.get(0).getFirstSaidText();
	} //public String getFirstSaidText()

	/**
	 * 获取说出的文字内容。
	 * @return 文字内容。
	 */
	public String getSaidText()
	{
		StringBuilder result= new StringBuilder(); //结果。

		for(VoiceRecognizeResultWs currentWs:ws) //一个个单词地拼接。
		{
			String currentText=currentWs.getSaidText(); //获取文字内容。

			result.append(currentText); //拼接。
		} //for(VoiceRecognizeResultWs currentWs:ws) //一个个单词地拼接。



		return result.toString();
	} //getSaidText

	private ArrayList<VoiceRecognizeResultWs> ws; //词语列表。

}
